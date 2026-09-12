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

    // Estado fino usado pelas telas de chamada/DTMF pra pedir "não deixa a
    // tela apagar agora" / "ignora toque do usuário pra fins de wake" --
    // substitui o antigo mecanismo de poke-lock (IPowerManager interno).
    // Não existe API pública equivalente a nível de sistema; isso é
    // resolvido na própria Activity via FLAG_KEEP_SCREEN_ON (ver
    // updateWakeState() abaixo), então aqui só guardamos a intenção.
    private boolean mIgnoreTouchUserActivity = false;
    private boolean mRestoreMuteOnInCallResume = false;

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

    /**
     * Acorda a tela imediatamente. Chamado quando um evento externo (nova
     * DisplayInfo da rede CDMA, broadcast de chamada de saída) precisa
     * garantir que o usuário veja a tela na hora -- mesmo mecanismo de
     * pokeUserActivity(), exposto com o nome que o código legado espera.
     */
    /* package */ void wakeUpScreen() {
        pokeUserActivity();
    }

    /**
     * Substitui o antigo poke-lock de "impede a tela de apagar" -- agora é
     * só um wake pontual (ver pokeUserActivity()); a Activity de chamada é
     * quem de fato mantém a tela acesa via FLAG_KEEP_SCREEN_ON enquanto
     * estiver em primeiro plano. `screenOnImmediately` é ignorado de
     * propósito (não há mais um "modo imediato" separado no PowerManager
     * público).
     */
    /* package */ void preventScreenOn(boolean screenOnImmediately) {
        if (screenOnImmediately) {
            pokeUserActivity();
        }
    }

    /**
     * Reavalia o wake lock com base no estado atual de chamada. Sem o
     * poke-lock interno, isso vira só "garante que a tela está acesa
     * agora" -- quem quer manter acesa por mais tempo usa
     * FLAG_KEEP_SCREEN_ON na própria janela.
     */
    /* package */ void updateWakeState() {
        pokeUserActivity();
    }

    /* package */ boolean getIgnoreTouchUserActivity() {
        return mIgnoreTouchUserActivity;
    }

    /* package */ void setIgnoreTouchUserActivity(boolean ignore) {
        mIgnoreTouchUserActivity = ignore;
    }

    /* package */ boolean getRestoreMuteOnInCallResume() {
        return mRestoreMuteOnInCallResume;
    }

    /* package */ void setRestoreMuteOnInCallResume(boolean restore) {
        mRestoreMuteOnInCallResume = restore;
    }

    KeyguardManager getKeyguardManager() {
        return mKeyguardManager;
    }

    /**
     * Duração do timeout de tela pedido por telas de chamada/discagem
     * enquanto estão em primeiro plano. Antigo mecanismo interno de
     * "poke lock" (LocalPowerManager) não existe mais em nenhuma API
     * pública -- ver setScreenTimeout() abaixo pro substituto.
     */
    public enum ScreenTimeoutDuration {
        SHORT,
        MEDIUM,
        DEFAULT
    }

    /**
     * Suspende o keyguard enquanto uma tela de chamada/discagem estiver em
     * primeiro plano (InCallScreen, EmergencyDialer). Usa o mesmo
     * KeyguardManager.KeyguardLock já criado em onCreate() -- API pública,
     * porém deprecated desde a API 13 e sem efeito garantido em todo
     * fabricante/versão; app.disableKeyguard()/reenableKeyguard() só cobrem
     * o caso "melhor esforço". Pra telas que realmente precisam aparecer
     * sobre a tela bloqueada, o caminho moderno é
     * Activity.setShowWhenLocked(true)/setTurnScreenOn(true) na própria
     * Activity (API 27+), não algo que dá pra centralizar aqui.
     */
    void disableKeyguard() {
        if (mKeyguardLock != null) {
            mKeyguardLock.disableKeyguard();
        }
    }

    void reenableKeyguard() {
        if (mKeyguardLock != null) {
            mKeyguardLock.reenableKeyguard();
        }
    }

    /**
     * Antigo poke-lock (IPowerManager/LocalPowerManager, API interna) não
     * existe mais. O substituto público exigiria WRITE_SETTINGS concedida
     * pelo usuário (Settings.canWrite()) pra mexer no
     * Settings.System.SCREEN_OFF_TIMEOUT global do aparelho -- mexer no
     * timeout do sistema inteiro por causa de uma tela de chamada é
     * invasivo demais, então esta versão é no-op de propósito. Quem
     * precisa manter a tela acesa (ex: durante uma chamada) deve usar
     * FLAG_KEEP_SCREEN_ON na própria janela da Activity.
     */
    void setScreenTimeout(ScreenTimeoutDuration duration) {
        // Intencionalmente no-op -- ver comentário acima.
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
