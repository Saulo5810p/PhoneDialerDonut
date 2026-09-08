/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

// REESCRITO PARA A ROTA A (DonutCallManager / android.telecom.Call).
//
// O NotificationMgr original (952 linhas) dependia de com.android.internal.
// telephony.{Phone,Call,Connection,CallerInfo,CallerInfoAsyncQuery} pra saber
// o estado da chamada, e de android.app.StatusBarManager (classe @hide,
// nunca fez parte do SDK público -- só processos de sistema conseguem usar)
// pra desenhar os ícones avulsos de mudo/viva-voz na barra de status.
// Nenhuma das duas coisas existe pra um app normal em nenhuma versão do
// Android. Trocado por:
//
//   - Fonte de verdade: DonutCallManager (android.telecom.Call), a mesma
//     usada por CallNotifier/PhoneUtils/CallCard. Este arquivo virou também
//     um DonutCallManager.Listener, então não depende mais de ninguém
//     chamando updateNotifications() manualmente feito no Handler original.
//   - Ícones de mudo/viva-voz: como StatusBarManager.addIcon() não tem
//     substituto público, o estado passou a ser mostrado (a) direto na tela
//     de chamada, no InCallMenu, que já indica mudo/viva-voz via
//     setIndicatorState() -- ver InCallMenu.updateMenu() -- e (b) como um
//     sufixo de texto na notificação de chamada em andamento, que é visível
//     mesmo com o app minimizado. Nenhuma informação foi perdida, só o
//     mecanismo de exibição mudou pra um que é permitido a um app comum.
//   - Chamada perdida: a versão original fazia uma query manual no
//     CallLog procurando linhas "type=MISSED AND new=1". A versão nova
//     detecta isso direto no evento onCallRemoved(), olhando
//     call.getDetails().getDisconnectCause().getCode() ==
//     DisconnectCause.MISSED (API pública, android.telecom) -- mais simples
//     e não depende de nenhuma leitura própria do call log.
//   - MWI (voicemail): getVoiceMailNumber()/getVoiceMessageCount() viraram
//     chamadas em TelephonyManager (API pública); a espera por
//     "SIM ainda carregando" (getIccRecordsLoaded(), API interna) foi
//     removida -- TelephonyManager já devolve null/0 nesse caso e o próximo
//     updateMwi() (disparado pelo sistema de novo) corrige sozinho.
//   - Notificações passaram a usar NotificationChannel (obrigatório desde o
//     Android 8/API 26; o app antigo de 2009 não precisava disso).
//
// API pública deste arquivo (init/getDefault/notifyECBM/
// cancelEcbmNotification/cancelMissedCallNotification) foi mantida idêntica
// à original -- é o que EmergencyCallbackMode.java e (quando migrado)
// PhoneInterfaceManager.java já chamam.

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.List;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.phone.compat.CallerInfo;
import com.android.phone.compat.CallerInfoAsyncQuery;
import com.android.phone.compat.CallerInfoAsyncQuery.OnQueryCompleteListener;
import com.android.phone.telecom.DonutCallManager;

/**
 * NotificationManager-related utility code for the Phone app.
 */
public class NotificationMgr implements DonutCallManager.Listener, OnQueryCompleteListener {
    private static final String LOG_TAG = "NotificationMgr";
    private static final boolean DBG = false;

    // notification types (mesmos ids da versão original)
    static final int MISSED_CALL_NOTIFICATION = 1;
    static final int IN_CALL_NOTIFICATION = 2;
    static final int CALL_FORWARD_NOTIFICATION = 6;
    static final int DATA_DISCONNECTED_ROAMING_NOTIFICATION = 7;
    static final int ECBM_NOTIFICATION = 8;
    static final int VOICEMAIL_NOTIFICATION = 5;

    private static final String CHANNEL_CALL_STATUS = "phone_call_status";
    private static final String CHANNEL_CALLS = "phone_missed_calls";

    private static NotificationMgr sMe = null;

    private final Context mContext;
    private final NotificationManagerCompat mNotificationMgr;
    private final DonutCallManager mCallManager = DonutCallManager.getInstance();
    private Toast mToast;

    // contador de chamadas perdidas (zera quando o usuário abre o call log)
    private int mNumberMissedCalls = 0;

    // notificação de chamada em andamento sendo mostrada agora (ou null)
    private Notification mInCallNotification;
    private RemoteViews mInCallContentView;

    private NotificationMgr(Context context) {
        mContext = context;
        // NotificationManagerCompat: cria canal (obrigatório desde o Android
        // 8/API 26) sem precisar checar Build.VERSION na mão -- em versões
        // anteriores as chamadas de canal viram no-op sozinhas.
        mNotificationMgr = NotificationManagerCompat.from(context);
        createNotificationChannels();
        mCallManager.addListener(this);
    }

    static void init(Context context) {
        sMe = new NotificationMgr(context);
    }

    static NotificationMgr getDefault() {
        return sMe;
    }

    private void createNotificationChannels() {
        NotificationChannelCompat status = new NotificationChannelCompat.Builder(
                CHANNEL_CALL_STATUS, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(mContext.getString(R.string.dialerIconLabel))
                .setDescription("Chamada em andamento, encaminhamento, roaming")
                .setShowBadge(false)
                .build();
        mNotificationMgr.createNotificationChannel(status);

        NotificationChannelCompat calls = new NotificationChannelCompat.Builder(
                CHANNEL_CALLS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(mContext.getString(R.string.notification_missedCallsTitle))
                .build();
        mNotificationMgr.createNotificationChannel(calls);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    // ------------------------------------------------------------------
    // DonutCallManager.Listener -- fonte de verdade das chamadas ativas
    // ------------------------------------------------------------------

    @Override
    public void onCallAdded(Call call) {
        updateInCallNotification();
    }

    @Override
    public void onCallStateChanged(Call call) {
        updateInCallNotification();
    }

    @Override
    public void onCallRemoved(Call call) {
        if (wasMissedCall(call)) {
            handleMissedCall(call);
        }
        updateInCallNotification();
    }

    @Override
    public void onAudioStateChanged(CallAudioState state) {
        // Mudo/viva-voz também aparecem na notificação de chamada em
        // andamento (ver comentário de topo) -- basta reconstruir ela.
        updateInCallNotification();
    }

    private static boolean wasMissedCall(Call call) {
        if (call == null || call.getDetails() == null) {
            return false;
        }
        DisconnectCause cause = call.getDetails().getDisconnectCause();
        return cause != null && cause.getCode() == DisconnectCause.MISSED;
    }

    private static String getNumberFromCall(Call call) {
        if (call == null || call.getDetails() == null) {
            return null;
        }
        Uri handle = call.getDetails().getHandle();
        return handle != null ? handle.getSchemeSpecificPart() : null;
    }

    // ------------------------------------------------------------------
    // Chamada perdida
    // ------------------------------------------------------------------

    private void handleMissedCall(Call call) {
        String number = getNumberFromCall(call);
        long date = System.currentTimeMillis();
        // Busca o nome do contato (se existir) em background antes de
        // notificar -- mesmo padrão de CallerInfoAsyncQuery usado no resto
        // do app.
        CallerInfoAsyncQuery.startQuery(mContext, number, this, new MissedCallInfo(number, date));
    }

    /** Cookie usado só pela consulta de chamada perdida (distingue da consulta da tela de chamada). */
    private static final class MissedCallInfo {
        final String number;
        final long date;
        MissedCallInfo(String number, long date) {
            this.number = number;
            this.date = date;
        }
    }

    @Override
    public void onQueryComplete(CallerInfo info, Object cookie) {
        if (cookie instanceof MissedCallInfo) {
            MissedCallInfo missed = (MissedCallInfo) cookie;
            String name = (info != null && !TextUtils.isEmpty(info.name)) ? info.name : null;
            notifyMissedCall(name, missed.number, missed.date);
        } else if (cookie instanceof RemoteViews) {
            // Callback da notificação de chamada em andamento (ver
            // updateInCallNotification()).
            RemoteViews contentView = (RemoteViews) cookie;
            contentView.setTextViewText(R.id.text2,
                    PhoneUtils.getCompactNameFromCallerInfo(info, mContext) + audioSuffix());
            if (mInCallNotification != null && contentView == mInCallContentView) {
                mNotificationMgr.notify(IN_CALL_NOTIFICATION, mInCallNotification);
            }
        }
    }

    /**
     * Exibe a notificação de chamada perdida.
     *
     * @param name nome do contato, ou null se desconhecido/sem contato
     * @param number número da chamada, ou null se indisponível
     * @param date instante (System.currentTimeMillis()) da chamada perdida
     */
    void notifyMissedCall(String name, String number, long date) {
        mNumberMissedCalls++;

        String callName;
        if (!TextUtils.isEmpty(name)) {
            callName = name;
        } else if (!TextUtils.isEmpty(number)) {
            callName = number;
        } else {
            callName = mContext.getString(R.string.unknown);
        }

        String title;
        String text;
        if (mNumberMissedCalls == 1) {
            title = mContext.getString(R.string.notification_missedCallTitle);
            text = callName;
        } else {
            title = mContext.getString(R.string.notification_missedCallsTitle);
            text = mContext.getString(R.string.notification_missedCallsMsg, mNumberMissedCalls);
        }

        Intent intent = PhoneApp.createCallLogIntent();
        PendingIntent contentIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Notification notification = new NotificationCompat.Builder(mContext, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.stat_notify_missed_call)
                .setWhen(date)
                .setShowWhen(true)
                .setContentTitle(title)
                .setContentText(text)
                .setTicker(mContext.getString(R.string.notification_missedCallTicker, callName))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();

        mNotificationMgr.notify(MISSED_CALL_NOTIFICATION, notification);
    }

    void cancelMissedCallNotification() {
        mNumberMissedCalls = 0;
        mNotificationMgr.cancel(MISSED_CALL_NOTIFICATION);
    }

    // ------------------------------------------------------------------
    // Chamada em andamento (ícone + notificação expandida da barra de status)
    // ------------------------------------------------------------------

    void updateInCallNotification() {
        List<Call> calls = mCallManager.getCalls();

        Call activeCall = findCallInState(calls, Call.STATE_ACTIVE);
        Call holdingCall = findCallInState(calls, Call.STATE_HOLDING);
        boolean hasActiveCall = activeCall != null;
        boolean hasHoldingCall = holdingCall != null;

        if (!hasActiveCall && !hasHoldingCall) {
            cancelInCallNotification();
            return;
        }

        Call currentCall = hasActiveCall ? activeCall : holdingCall;

        int resId;
        if (!hasActiveCall && hasHoldingCall) {
            resId = R.drawable.stat_sys_phone_call_on_hold;
        } else if (PhoneUtils.getMute()) {
            resId = R.drawable.stat_notify_call_mute;
        } else {
            resId = R.drawable.stat_sys_phone_call;
        }

        RemoteViews contentView =
                new RemoteViews(mContext.getPackageName(), R.layout.ongoing_call_notification);
        contentView.setImageViewResource(R.id.icon, resId);

        String line1;
        if (hasHoldingCall && !hasActiveCall) {
            line1 = mContext.getString(R.string.notification_on_hold);
        } else {
            line1 = mContext.getString(R.string.notification_ongoing_call_format);
        }

        long connectTimeMillis = currentCall.getDetails() != null
                ? currentCall.getDetails().getConnectTimeMillis() : 0;
        if (connectTimeMillis > 0) {
            long chronometerBaseTime =
                    SystemClock.elapsedRealtime() - (System.currentTimeMillis() - connectTimeMillis);
            contentView.setChronometer(R.id.text1, chronometerBaseTime, line1, true);
        } else {
            contentView.setTextViewText(R.id.text1, line1);
        }

        String line2;
        if (PhoneUtils.isConferenceCall(currentCall)) {
            line2 = mContext.getString(R.string.card_title_conf_call) + audioSuffix();
            contentView.setTextViewText(R.id.text2, line2);
        } else {
            // Nome ainda não carregado -- mostra o número cru por enquanto,
            // igual à versão original, e atualiza pra nome real quando a
            // consulta assíncrona (onQueryComplete) voltar.
            String number = getNumberFromCall(currentCall);
            line2 = (TextUtils.isEmpty(number) ? mContext.getString(R.string.unknown) : number)
                    + audioSuffix();
            contentView.setTextViewText(R.id.text2, line2);
        }

        Notification notification = new NotificationCompat.Builder(mContext, CHANNEL_CALL_STATUS)
                .setSmallIcon(resId)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(mContext, 0,
                        PhoneApp.createInCallIntent(),
                        PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()))
                .setCustomContentView(contentView)
                .build();

        mInCallNotification = notification;
        mInCallContentView = contentView;
        mNotificationMgr.notify(IN_CALL_NOTIFICATION, notification);

        if (!PhoneUtils.isConferenceCall(currentCall)) {
            PhoneUtils.startGetCallerInfo(mContext, currentCall, this, contentView);
        }
    }

    private String audioSuffix() {
        CallAudioState state = mCallManager.getAudioState();
        StringBuilder sb = new StringBuilder();
        if (state != null && state.isMuted()) {
            sb.append(" \u2022 ").append(mContext.getString(R.string.menu_mute));
        }
        if (state != null && state.getRoute() == CallAudioState.ROUTE_SPEAKER) {
            sb.append(" \u2022 ").append(mContext.getString(R.string.menu_speaker));
        }
        return sb.toString();
    }

    private static Call findCallInState(List<Call> calls, int state) {
        for (Call c : calls) {
            if (c.getState() == state) {
                return c;
            }
        }
        return null;
    }

    private void cancelInCallNotification() {
        mInCallNotification = null;
        mInCallContentView = null;
        mNotificationMgr.cancel(IN_CALL_NOTIFICATION);
    }

    /** Mantido pra call sites antigos -- cancela a notificação de chamada em andamento. */
    void cancelCallInProgressNotification() {
        cancelInCallNotification();
    }

    /** Mantidos como pontos de atualização explícitos -- hoje só redesenham a notificação. */
    void updateSpeakerNotification() {
        updateInCallNotification();
    }

    void updateMuteNotification() {
        updateInCallNotification();
    }

    // ------------------------------------------------------------------
    // Voicemail (MWI)
    // ------------------------------------------------------------------

    /**
     * Atualiza a notificação de recado de voicemail (MWI).
     *
     * @param visible true se há recados pendentes
     */
    /* package */ void updateMwi(boolean visible) {
        if (DBG) log("updateMwi(): " + visible);
        if (!visible) {
            mNotificationMgr.cancel(VOICEMAIL_NOTIFICATION);
            return;
        }

        TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        String vmNumber = null;
        int vmCount = 0;
        try {
            vmNumber = tm.getVoiceMailNumber();
            vmCount = tm.getVoiceMessageCount();
        } catch (SecurityException e) {
            // sem READ_PHONE_STATE ainda -- segue com valores vazios
        }

        String title;
        if (vmCount > 0) {
            title = String.format(
                    mContext.getString(R.string.notification_voicemail_title_count), vmCount);
        } else {
            title = mContext.getString(R.string.notification_voicemail_title);
        }

        String text;
        if (TextUtils.isEmpty(vmNumber)) {
            text = mContext.getString(R.string.notification_voicemail_no_vm_number);
        } else {
            text = String.format(
                    mContext.getString(R.string.notification_voicemail_text_format),
                    PhoneNumberUtils.formatNumber(vmNumber));
        }

        Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("voicemail", "", null));
        PendingIntent contentIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Notification notification = new NotificationCompat.Builder(mContext, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.stat_notify_voicemail)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();

        mNotificationMgr.notify(VOICEMAIL_NOTIFICATION, notification);
    }

    // ------------------------------------------------------------------
    // Encaminhamento de chamadas (CFI)
    // ------------------------------------------------------------------

    /* package */ void updateCfi(boolean visible) {
        if (DBG) log("updateCfi(): " + visible);
        if (!visible) {
            mNotificationMgr.cancel(CALL_FORWARD_NOTIFICATION);
            return;
        }

        Intent intent = new Intent(mContext, CallFeaturesSetting.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Notification notification = new NotificationCompat.Builder(mContext, CHANNEL_CALL_STATUS)
                .setSmallIcon(R.drawable.stat_sys_phone_call_forward)
                .setContentTitle(mContext.getString(R.string.labelCF))
                .setContentText(mContext.getString(R.string.sum_cfu_enabled_indicator))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();

        mNotificationMgr.notify(CALL_FORWARD_NOTIFICATION, notification);
    }

    // ------------------------------------------------------------------
    // Dados desconectados por roaming
    // ------------------------------------------------------------------

    /* package */ void showDataDisconnectedRoaming() {
        if (DBG) log("showDataDisconnectedRoaming()...");

        Intent intent = new Intent(mContext, Settings.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Notification notification = new NotificationCompat.Builder(mContext, CHANNEL_CALL_STATUS)
                .setSmallIcon(R.drawable.stat_sys_warning)
                .setContentTitle(mContext.getString(R.string.roaming))
                .setContentText(mContext.getString(R.string.roaming_reenable_message))
                .setContentIntent(contentIntent)
                .build();

        mNotificationMgr.notify(DATA_DISCONNECTED_ROAMING_NOTIFICATION, notification);
    }

    /* package */ void hideDataDisconnectedRoaming() {
        if (DBG) log("hideDataDisconnectedRoaming()...");
        mNotificationMgr.cancel(DATA_DISCONNECTED_ROAMING_NOTIFICATION);
    }

    // ------------------------------------------------------------------
    // Modo de retorno de chamada de emergência (ECBM)
    // ------------------------------------------------------------------

    void notifyECBM() {
        Intent intent = new Intent(mContext, EmergencyCallbackMode.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Notification notification = new NotificationCompat.Builder(mContext, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.stat_sys_warning)
                .setTicker(mContext.getString(R.string.ecbm_mode_text))
                .setContentTitle(mContext.getString(R.string.ecbm_mode_text))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();

        mNotificationMgr.notify(ECBM_NOTIFICATION, notification);
    }

    void cancelEcbmNotification() {
        mNotificationMgr.cancel(ECBM_NOTIFICATION);
    }

    // ------------------------------------------------------------------
    // Aviso rápido (Toast) -- sem mudança de comportamento
    // ------------------------------------------------------------------

    /* package */ void postTransientNotification(int notifyId, CharSequence msg) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
        mToast.show();
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[NotificationMgr] " + msg);
    }
}
