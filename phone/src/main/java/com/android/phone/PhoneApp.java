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

// REESCRITO PARA A ROTA A — versão substancialmente reduzida.
//
// O PhoneApp original (1198 linhas) era dono da inicialização de todo o motor
// de telefonia (Phone/PhoneFactory/IccCard via com.android.internal.telephony),
// controle de wake lock de baixo nível via IPowerManager/LocalPowerManager
// (também APIs internas), tratamento de MMI, mudança de tecnologia de rádio
// GSM<->CDMA, cache de PIN do SIM e fluxo de desbloqueio PUK, e indicação de
// Bluetooth. Nada disso está disponível a um app normal.
//
// Esta versão mantém só o que a UI atual (InCallScreen/CallCard/DTMF/
// CallFeaturesSetting/InCallMenu) realmente usa: o singleton do Application,
// Ringer+CallNotifier (já migrados), rastreamento da InCallScreen ativa, os
// intents de "voltar pra tela de chamada"/"call log", poke de tela usando
// PowerManager.WakeLock público (em vez do poke-lock interno), e
// showBluetoothIndication() consultando a rota de áudio real via
// PhoneUtils/DonutCallManager (o antigo BluetoothHandsfree -- stack de AT
// commands rodada pelo próprio app -- foi excluído do build; ver
// telecom/DonutCallManager.java).
//
// NÃO instancia NotificationMgr (ainda não migrado nesta entrega) nem
// PhoneInterfaceManager/BluetoothHandsfree (ambos viraram classes utilitárias
// estáticas ou foram excluídos do build -- não são mais "instanciáveis").

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public class PhoneApp extends Application {
    /* package */ static final String LOG_TAG = "PhoneApp";
    /* package */ static final int DBG_LEVEL = 1;
    private static final boolean DBG = false;

    public enum WakeState {
        SLEEP,
        PARTIAL,
        FULL
    }

    private static PhoneApp sMe;

    CallNotifier notifier;
    Ringer ringer;

    private InCallScreen mInCallScreen;
    private Activity mPUKEntryActivity;
    private ProgressDialog mPUKEntryProgressDialog;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private KeyguardManager mKeyguardManager;
    private KeyguardManager.KeyguardLock mKeyguardLock;

    public PhoneApp() {
        sMe = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) Log.d(LOG_TAG, "onCreate()...");

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                LOG_TAG);
        mKeyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        mKeyguardLock = mKeyguardManager.newKeyguardLock(LOG_TAG);

        ringer = new Ringer(this);
        notifier = new CallNotifier(this, ringer);

        // NotificationMgr.init() faltava aqui -- o arquivo foi migrado numa
        // sessão anterior, mas ninguém chamava init() de verdade, então
        // getDefault() (usado por PhoneInterfaceManager.
        // cancelMissedCallsNotification() e por EmergencyCallbackMode)
        // sempre devolvia null. Precisa rodar uma vez no onCreate(), como
        // Ringer/CallNotifier -- o construtor de NotificationMgr já se
        // registra como DonutCallManager.Listener sozinho.
        NotificationMgr.init(this);
    }

    static PhoneApp getInstance() {
        return sMe;
    }

    Ringer getRinger() {
        return ringer;
    }

    static Intent createCallLogIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW, null);
        intent.setType("vnd.android.cursor.dir/calls");
        return intent;
    }

    /* package */ static Intent createInCallIntent() {
        return createInCallIntent(false);
    }

    /* package */ static Intent createInCallIntent(boolean showDialpad) {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        intent.setClassName("com.xaulinxs.donut.telefoneantigo",
                getCallScreenClassName());
        if (showDialpad) {
            intent.putExtra("showDialpad", true);
        }
        return intent;
    }

    static String getCallScreenClassName() {
        return InCallScreen.class.getName();
    }

    void displayCallScreen() {
        startActivity(createInCallIntent());
    }

    boolean isSimPinEnabled() {
        // Cache/desbloqueio de PIN do SIM (mIsSimPinEnabled/mCachedSimPin no
        // original) dependia do fluxo de eventos EVENT_SIM_LOCKED via IccCard
        // interno — não religado nesta versão. Sempre "desbloqueado" por ora.
        return false;
    }

    void setInCallScreenInstance(InCallScreen inCallScreen) {
        mInCallScreen = inCallScreen;
    }

    boolean isShowingCallScreen() {
        if (mInCallScreen == null) return false;
        return mInCallScreen.isForegroundActivity();
    }

    void dismissCallScreen() {
        if (mInCallScreen != null) {
            mInCallScreen.finish();
        }
    }

    void setPukEntryActivity(Activity activity) {
        mPUKEntryActivity = activity;
    }

    Activity getPUKEntryActivity() {
        return mPUKEntryActivity;
    }

    void setPukEntryProgressDialog(ProgressDialog dialog) {
        mPUKEntryProgressDialog = dialog;
    }

    ProgressDialog getPUKEntryProgressDialog() {
        return mPUKEntryProgressDialog;
    }

    /**
     * Acorda a tela por um instante — versão simplificada usando
     * PowerManager.WakeLock público (a original usava um sistema de
     * "poke lock" via IPowerManager/LocalPowerManager, APIs internas).
     */
    /* package */ void pokeUserActivity() {
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire(3000);
        }
    }

    KeyguardManager getKeyguardManager() {
        return mKeyguardManager;
    }

    boolean isHeadsetPlugged() {
        // Detecção de fone com fio via broadcast ACTION_HEADSET_PLUG (API
        // pública) não religada nesta versão — assume "sem fone" por ora.
        return false;
    }

    /**
     * Indica se o áudio da chamada está roteado pro Bluetooth agora.
     * Substitui o antigo BluetoothHandsfree (stack de AT commands rodada
     * pelo próprio app, removida do SDK público -- ver
     * telecom/DonutCallManager.java para o porquê). O sistema Telecom
     * moderno já gerencia pareamento/SCO/protocolo HFP sozinho; aqui só
     * consultamos a rota de áudio atual da chamada (API pública).
     */
    /* package */ boolean showBluetoothIndication() {
        return PhoneUtils.isBluetoothAudioOn();
    }

    /* package */ void updateBluetoothIndication(boolean forceUiUpdate) {
        if (forceUiUpdate && isShowingCallScreen()) {
            mInCallScreen.updateBluetoothIndication();
        }
    }
}
