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
 *
 * ============================================================================
 * ADAPTADO (Rota A / DonutCallManager) — mudanças em relação ao 1.6 original:
 *
 * 1. Não existe mais com.android.internal.telephony.{Phone,Call,Connection,
 *    CallerInfo,CallerInfoAsyncQuery} — o estado das chamadas agora vem do
 *    DonutCallManager (android.telecom.Call), e CallerInfo/CallerInfoAsyncQuery
 *    são as versões públicas em com.android.phone.compat.
 * 2. android.pim.ContactsAsyncHelper (API interna, oculta) foi removido —
 *    a foto do contato agora é carregada por uma AsyncTask local simples
 *    (LoadPhotoTask), usando o photoUri já resolvido pelo compat/CallerInfo.
 * 3. CallTime (helper de 2009 amarrado a Connection/Call internos) não é
 *    mais usado — o cronômetro da chamada em andamento é um Runnable
 *    simples postado a cada 1s neste próprio View, usando
 *    Call.Details.getConnectTimeMillis()/SystemClock.elapsedRealtime().
 * 4. Toda a distinção GSM/CDMA (3-way call CDMA, CNAP, etc.) foi removida —
 *    o telecom framework do sistema já normaliza isso; o app só vê
 *    android.telecom.Call, sem diferenciar a tecnologia por baixo.
 * 5. "Conference call" agora é detectado via
 *    Call.Details.PROPERTY_CONFERENCE (PhoneUtils.isConferenceCall()),
 *    não mais por contagem de Connections.
 * 6. Suporte a Bluetooth (ícones/fundos "_bluetooth_") mantido na UI, mas
 *    sempre falso por ora (PhoneApp.showBluetoothIndication() — BT
 *    handsfree ainda não foi migrado); o caminho de código já está pronto
 *    para quando for.
 * ============================================================================
 */

package com.android.phone;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.telecom.Call;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.phone.compat.CallerInfo;
import com.android.phone.compat.CallerInfoAsyncQuery;
import com.android.phone.telecom.DonutCallManager;

import java.io.InputStream;

/**
 * "Call card" UI element: shows who you're talking to and the state of the
 * current call(s). Versão adaptada para operar sobre android.telecom.Call
 * (via DonutCallManager) em vez do motor de telefonia interno do AOSP.
 */
public class CallCard extends FrameLayout implements CallerInfoAsyncQuery.OnQueryCompleteListener {
    private static final String LOG_TAG = "CallCard";
    private static final boolean DBG = true;

    /** Activity dona deste CallCard (pode ser null antes de attach / após destroy). */
    private InCallScreen mInCallScreen;
    private PhoneApp mApplication;

    // Top-level subviews
    private ViewGroup mMainCallCard;
    private ViewGroup mOtherCallOngoingInfoArea;
    private ViewGroup mOtherCallOnHoldInfoArea;

    private TextView mUpperTitle;
    private ViewGroup mLowerTitleViewGroup;
    private TextView mLowerTitle;
    private ImageView mLowerTitleIcon;
    private TextView mElapsedTime;

    private int mTextColorConnected;
    private int mTextColorConnectedBluetooth;
    private int mTextColorEnded;
    private int mTextColorOnHold;

    private ImageView mPhoto;
    private TextView mName;
    private TextView mPhoneNumber;
    private TextView mLabel;

    private ImageView mOtherCallOngoingIcon;
    private TextView mOtherCallOngoingName;
    private TextView mOtherCallOngoingStatus;
    private TextView mOtherCallOnHoldName;
    private TextView mOtherCallOnHoldStatus;

    private TextView mMenuButtonHint;

    // Evita disparar consulta de CallerInfo repetida para a mesma chamada.
    private Call mCallerInfoQueryTarget;
    private Uri mLastLoadedPhotoUri;

    // Cronômetro simples do tempo de chamada ativa (substitui CallTime).
    private final Handler mTimerHandler = new Handler();
    private Call mTimedCall;
    private final Runnable mTimerTick = new Runnable() {
        @Override
        public void run() {
            if (mTimedCall == null) return;
            long connectTime = mTimedCall.getDetails() != null
                    ? mTimedCall.getDetails().getConnectTimeMillis() : 0;
            if (connectTime > 0) {
                long elapsedSeconds = (System.currentTimeMillis() - connectTime) / 1000;
                if (elapsedSeconds < 0) elapsedSeconds = 0;
                mElapsedTime.setText(DateUtils.formatElapsedTime(elapsedSeconds));
            }
            mTimerHandler.postDelayed(this, 1000);
        }
    };

    public CallCard(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (DBG) log("CallCard constructor...");

        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.call_card, this, true);

        mApplication = PhoneApp.getInstance();
    }

    void setInCallScreenInstance(InCallScreen inCallScreen) {
        mInCallScreen = inCallScreen;
    }

    /* package */ void stopTimer() {
        mTimerHandler.removeCallbacks(mTimerTick);
        mTimedCall = null;
    }

    private void startTimer(Call call) {
        mTimedCall = call;
        mTimerHandler.removeCallbacks(mTimerTick);
        mTimerHandler.post(mTimerTick);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (DBG) log("CallCard onFinishInflate()...");

        mMainCallCard = (ViewGroup) findViewById(R.id.mainCallCard);
        mOtherCallOngoingInfoArea = (ViewGroup) findViewById(R.id.otherCallOngoingInfoArea);
        mOtherCallOnHoldInfoArea = (ViewGroup) findViewById(R.id.otherCallOnHoldInfoArea);

        mUpperTitle = (TextView) findViewById(R.id.upperTitle);
        mLowerTitleViewGroup = (ViewGroup) findViewById(R.id.lowerTitleViewGroup);
        mLowerTitle = (TextView) findViewById(R.id.lowerTitle);
        mLowerTitleIcon = (ImageView) findViewById(R.id.lowerTitleIcon);
        mElapsedTime = (TextView) findViewById(R.id.elapsedTime);

        mTextColorConnected = getResources().getColor(R.color.incall_textConnected);
        mTextColorConnectedBluetooth =
                getResources().getColor(R.color.incall_textConnectedBluetooth);
        mTextColorEnded = getResources().getColor(R.color.incall_textEnded);
        mTextColorOnHold = getResources().getColor(R.color.incall_textOnHold);

        mPhoto = (ImageView) findViewById(R.id.photo);
        mName = (TextView) findViewById(R.id.name);
        mPhoneNumber = (TextView) findViewById(R.id.phoneNumber);
        mLabel = (TextView) findViewById(R.id.label);

        mOtherCallOngoingIcon = (ImageView) findViewById(R.id.otherCallOngoingIcon);
        mOtherCallOngoingName = (TextView) findViewById(R.id.otherCallOngoingName);
        mOtherCallOngoingStatus = (TextView) findViewById(R.id.otherCallOngoingStatus);
        mOtherCallOnHoldName = (TextView) findViewById(R.id.otherCallOnHoldName);
        mOtherCallOnHoldStatus = (TextView) findViewById(R.id.otherCallOnHoldStatus);

        mMenuButtonHint = (TextView) findViewById(R.id.menuButtonHint);
    }

    /**
     * Atualiza todos os elementos da UI a partir do estado atual do
     * DonutCallManager. Chamado pela InCallScreen sempre que uma chamada
     * é adicionada/removida ou muda de estado.
     */
    void updateState() {
        if (DBG) log("updateState()...");

        DonutCallManager mgr = DonutCallManager.getInstance();
        Call ringingCall = findCallInState(mgr, Call.STATE_RINGING);
        Call activeCall = findActiveOrDialingCall(mgr);
        Call holdingCall = findCallInState(mgr, Call.STATE_HOLDING);

        if (ringingCall != null) {
            // Chamada entrando (com ou sem outra já em andamento).
            displayMainCallStatus(ringingCall);
            displayOnHoldCallStatus(holdingCall);
            displayOngoingCallStatus(activeCall);
        } else if (activeCall != null || holdingCall != null) {
            Call mainCall = (activeCall != null) ? activeCall : holdingCall;
            Call otherHold = (activeCall != null) ? holdingCall : null;
            displayMainCallStatus(mainCall);
            displayOnHoldCallStatus(otherHold);
            displayOngoingCallStatus(null);
        } else {
            // Sem nenhuma chamada viva; se houver alguma DISCONNECTED
            // ainda pendurada (tela de "chamada encerrada"), mostra ela.
            Call disconnected = findCallInState(mgr, Call.STATE_DISCONNECTED);
            if (disconnected != null) {
                displayMainCallStatus(disconnected);
                displayOnHoldCallStatus(null);
                displayOngoingCallStatus(null);
            } else {
                displayMainCallStatus(null);
                displayOnHoldCallStatus(null);
                displayOngoingCallStatus(null);
            }
        }
    }

    private static Call findCallInState(DonutCallManager mgr, int state) {
        for (Call c : mgr.getCalls()) {
            if (c.getState() == state) return c;
        }
        return null;
    }

    private static Call findActiveOrDialingCall(DonutCallManager mgr) {
        for (Call c : mgr.getCalls()) {
            int s = c.getState();
            if (s == Call.STATE_ACTIVE || s == Call.STATE_DIALING
                    || s == Call.STATE_CONNECTING || s == Call.STATE_DISCONNECTING) {
                return c;
            }
        }
        return null;
    }

    /**
     * Atualiza o bloco principal (foto/nome/número/título) a partir da
     * chamada informada (pode ser a que está tocando, a ativa, ou a que
     * ficou em espera se for a única). {@code call} == null significa
     * "sem nada para mostrar" (telefone realmente ocioso).
     */
    private void displayMainCallStatus(Call call) {
        if (DBG) log("displayMainCallStatus(" + call + ")...");

        if (call == null) {
            mMainCallCard.setVisibility(View.GONE);
            stopTimer();
            return;
        }
        mMainCallCard.setVisibility(View.VISIBLE);

        int state = call.getState();
        if (DBG) log("  - call.state: " + state);

        boolean landscapeMode = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        boolean bluetoothActive = mApplication.showBluetoothIndication();

        int callCardBackgroundResid;
        switch (state) {
            case Call.STATE_ACTIVE:
                callCardBackgroundResid = bluetoothActive
                        ? (landscapeMode ? R.drawable.incall_frame_bluetooth_tall_land
                                         : R.drawable.incall_frame_bluetooth_tall_port)
                        : (landscapeMode ? R.drawable.incall_frame_connected_tall_land
                                         : R.drawable.incall_frame_connected_tall_port);
                startTimer(call);
                break;

            case Call.STATE_HOLDING:
                callCardBackgroundResid = landscapeMode
                        ? R.drawable.incall_frame_hold_tall_land
                        : R.drawable.incall_frame_hold_tall_port;
                stopTimer();
                break;

            case Call.STATE_DISCONNECTED:
            case Call.STATE_DISCONNECTING:
                callCardBackgroundResid = landscapeMode
                        ? R.drawable.incall_frame_ended_tall_land
                        : R.drawable.incall_frame_ended_tall_port;
                stopTimer();
                break;

            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING:
            case Call.STATE_RINGING:
            default:
                callCardBackgroundResid = bluetoothActive
                        ? (landscapeMode ? R.drawable.incall_frame_bluetooth_tall_land
                                         : R.drawable.incall_frame_bluetooth_tall_port)
                        : (landscapeMode ? R.drawable.incall_frame_normal_tall_land
                                         : R.drawable.incall_frame_normal_tall_port);
                stopTimer();
                break;
        }

        setMainCallCardBackgroundResource(callCardBackgroundResid);
        updateCardTitleWidgets(call);

        // Nome/número/foto: dispara consulta de CallerInfo (uma vez por
        // chamada; chamadas subsequentes de updateState() para a mesma
        // Call não repetem a query).
        if (call != mCallerInfoQueryTarget) {
            mCallerInfoQueryTarget = call;
            PhoneUtils.startGetCallerInfo(getContext(), call, this, call);
            // Mostra algo razoável imediatamente (número puro) enquanto a
            // consulta assíncrona não volta.
            updateDisplayForPerson(PhoneUtils.getCallerInfo(getContext(), call), call);
        }

        if (PhoneUtils.isConferenceCall(call)) {
            mName.setText(R.string.card_title_conf_call);
            mPhoneNumber.setText("");
            mLabel.setText("");
        }
    }

    /** Implementado para CallerInfoAsyncQuery.OnQueryCompleteListener. */
    @Override
    public void onQueryComplete(CallerInfo info, Object cookie) {
        if (DBG) log("onQueryComplete: cookie=" + cookie + ", info=" + info);
        if (!(cookie instanceof Call)) return;
        Call call = (Call) cookie;
        if (call != mCallerInfoQueryTarget) {
            // Chegou tarde demais, a CallCard já está mostrando outra chamada.
            return;
        }
        updateDisplayForPerson(info, call);
    }

    /** Preenche nome/número/label/foto a partir de um CallerInfo já resolvido. */
    private void updateDisplayForPerson(CallerInfo info, Call call) {
        if (PhoneUtils.isConferenceCall(call)) {
            // O bloco de conferência já foi tratado em displayMainCallStatus().
            return;
        }

        String name = PhoneUtils.getCompactNameFromCallerInfo(info, getContext());
        mName.setText(name);

        boolean showLabelAndNumber = info != null && info.contactExists
                && !TextUtils.isEmpty(info.name);
        if (showLabelAndNumber) {
            mPhoneNumber.setText(info.phoneNumber);
            mLabel.setText(info.phoneLabel != null ? info.phoneLabel : "");
        } else {
            mPhoneNumber.setText("");
            mLabel.setText("");
        }

        loadPhoto(info);
    }

    private void loadPhoto(CallerInfo info) {
        Uri photoUri = (info != null) ? info.photoUri : null;
        if (photoUri == null) {
            mLastLoadedPhotoUri = null;
            mPhoto.setImageResource(R.drawable.picture_unknown);
            return;
        }
        if (photoUri.equals(mLastLoadedPhotoUri)) {
            return; // já está mostrando essa foto
        }
        mLastLoadedPhotoUri = photoUri;
        new LoadPhotoTask(mPhoto, photoUri, getContext()).execute();
    }

    /** AsyncTask enxuta para carregar a foto do contato sem API interna. */
    private static class LoadPhotoTask extends AsyncTask<Void, Void, Bitmap> {
        private final ImageView mTarget;
        private final Uri mUri;
        private final Context mContext;

        LoadPhotoTask(ImageView target, Uri uri, Context context) {
            mTarget = target;
            mUri = uri;
            mContext = context.getApplicationContext();
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try (InputStream in = mContext.getContentResolver().openInputStream(mUri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.w(LOG_TAG, "LoadPhotoTask: falha ao carregar foto: " + e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (mTarget == null) return;
            if (bitmap != null) {
                mTarget.setImageBitmap(bitmap);
            } else {
                mTarget.setImageResource(R.drawable.picture_unknown);
            }
        }
    }

    private void displayOnHoldCallStatus(Call call) {
        if (call == null) {
            mOtherCallOnHoldInfoArea.setVisibility(View.GONE);
            return;
        }
        mOtherCallOnHoldInfoArea.setVisibility(View.VISIBLE);
        CallerInfo info = PhoneUtils.getCallerInfo(getContext(), call);
        mOtherCallOnHoldName.setText(PhoneUtils.getCompactNameFromCallerInfo(info, getContext()));
        mOtherCallOnHoldStatus.setText(R.string.onHold);
    }

    private void displayOngoingCallStatus(Call call) {
        if (call == null) {
            mOtherCallOngoingInfoArea.setVisibility(View.GONE);
            return;
        }
        mOtherCallOngoingInfoArea.setVisibility(View.VISIBLE);
        boolean bluetoothActive = mApplication.showBluetoothIndication();
        mOtherCallOngoingIcon.setImageResource(bluetoothActive
                ? R.drawable.ic_incall_ongoing_bluetooth : R.drawable.ic_incall_ongoing);
        CallerInfo info = PhoneUtils.getCallerInfo(getContext(), call);
        mOtherCallOngoingName.setText(PhoneUtils.getCompactNameFromCallerInfo(info, getContext()));
        mOtherCallOngoingStatus.setText(R.string.ongoing);
    }

    private void updateCardTitleWidgets(Call call) {
        int state = call.getState();
        String cardTitle = getTitleForCallCard(call);
        if (DBG) log("updateCardTitleWidgets: " + cardTitle);

        boolean bluetoothActive = mApplication.showBluetoothIndication();

        if (state == Call.STATE_ACTIVE) {
            int ongoingCallIcon = bluetoothActive
                    ? R.drawable.ic_incall_ongoing_bluetooth : R.drawable.ic_incall_ongoing;
            int textColor = bluetoothActive ? mTextColorConnectedBluetooth : mTextColorConnected;

            mLowerTitleViewGroup.setVisibility(View.VISIBLE);
            mLowerTitleIcon.setImageResource(ongoingCallIcon);
            mLowerTitle.setText(cardTitle);
            mLowerTitle.setTextColor(textColor);
            mElapsedTime.setTextColor(textColor);
            mUpperTitle.setText("");
        } else if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            mLowerTitleViewGroup.setVisibility(View.VISIBLE);
            mLowerTitleIcon.setImageResource(R.drawable.ic_incall_end);
            mLowerTitle.setText(cardTitle);
            mLowerTitle.setTextColor(mTextColorEnded);
            mElapsedTime.setTextColor(mTextColorEnded);
            mUpperTitle.setText("");
        } else if (state == Call.STATE_HOLDING) {
            mLowerTitleViewGroup.setVisibility(View.VISIBLE);
            mLowerTitleIcon.setImageResource(R.drawable.ic_incall_onhold);
            mLowerTitle.setText(cardTitle);
            mLowerTitle.setTextColor(mTextColorOnHold);
            mElapsedTime.setTextColor(mTextColorOnHold);
            mUpperTitle.setText("");
        } else {
            // DIALING, CONNECTING, RINGING, etc.
            mUpperTitle.setText(cardTitle);
            mLowerTitleViewGroup.setVisibility(View.INVISIBLE);
        }

        if (state != Call.STATE_DISCONNECTED && state != Call.STATE_DISCONNECTING
                && state != Call.STATE_ACTIVE) {
            mElapsedTime.setText(DateUtils.formatElapsedTime(0));
        }
    }

    private String getTitleForCallCard(Call call) {
        int state = call.getState();
        if (PhoneUtils.isConferenceCall(call)) {
            return getContext().getString(R.string.card_title_conf_call);
        }
        switch (state) {
            case Call.STATE_RINGING:
                return getContext().getString(R.string.card_title_incoming_call);
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING:
                return getContext().getString(R.string.card_title_dialing);
            case Call.STATE_ACTIVE:
                return getContext().getString(R.string.card_title_in_progress);
            case Call.STATE_HOLDING:
                return getContext().getString(R.string.card_title_on_hold);
            case Call.STATE_DISCONNECTING:
                return getContext().getString(R.string.card_title_hanging_up);
            case Call.STATE_DISCONNECTED:
                return getContext().getString(R.string.card_title_call_ended);
            default:
                return getContext().getString(R.string.card_title_in_call);
        }
    }

    private void setMainCallCardBackgroundResource(int resId) {
        if (resId != 0) {
            mMainCallCard.setBackgroundResource(resId);
        }
    }

    /**
     * Atualiza o ícone de Bluetooth exibido na CallCard (fundo/ícones
     * "_bluetooth_"). Chamado pela InCallScreen quando o estado de BT muda.
     * Sem efeito real ainda (BT handsfree não migrado) — só reforça o
     * updateState() atual pra manter a UI coerente quando isso existir.
     */
    void updateBluetoothIndication() {
        updateState();
    }

    /** Controla a visibilidade do texto de dica ("Press Menu..."), se usado. */
    void setMenuButtonHintVisible(boolean visible) {
        if (mMenuButtonHint != null) {
            mMenuButtonHint.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
