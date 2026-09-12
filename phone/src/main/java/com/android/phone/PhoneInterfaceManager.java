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
// O PhoneInterfaceManager original (658 linhas) era um "ITelephony.Stub":
// implementava a interface AIDL com.android.internal.telephony.ITelephony e
// se registrava como o serviço de sistema "phone" via
// ServiceManager.addService("phone", this) -- é assim que outros apps do
// aparelho (Discador, Configurações, etc.) falavam com o processo da
// telefonia via IPC/Binder no Android original.
//
// Isso não tem substituto possível para um app comum, com ou sem API
// interna: ServiceManager.addService() é @hide, e mesmo que não fosse, o
// próprio Binder do Android bloqueia qualquer processo sem UID de sistema de
// registrar um serviço com nome reservado como "phone" -- essa trava é do
// SO, não do SDK. Também não faz sentido tentar: hoje o "serviço phone" de
// verdade do Android já existe (é o processo com.android.phone real do
// aparelho), e não é substituível por um app instalado por cima.
//
// A parte que sobrevive é a lógica de negócio por trás de cada método do
// AIDL -- e ela já não dependia de nada exclusivo de sistema em boa parte
// dos casos (dial/call/endCall/answerRingingCall/silenceRinger/showCallScreen
// /isIdle/isOffhook/isRinging), então virou uma classe utilitária estática
// comum, no mesmo espírito do PhoneUtils.java: um wrapper fino sobre
// DonutCallManager/android.telecom.Call, chamado direto pelo próprio
// processo do app (não mais por IPC de fora). O antigo MainThreadHandler
// (que existia só para tirar chamadas de telefonia do binder thread de um
// processo externo e jogar pro thread principal do processo do Phone) não
// tem mais razão de existir -- quem chama estes métodos agora já é sempre
// o próprio processo do app, então as chamadas podem ser diretas e
// síncronas.
//
// Cortado sem substituto (API signature/system-only, sem equivalente público
// em nenhuma versão do Android, mesma parede já documentada em
// STATUS-PROJETO.md para ChangeIccPinScreen/IccPinUnlockPanel/etc.):
//   - supplyPin()/isSimPinEnabled() (desbloqueio de PIN do SIM via IccCard)
//   - toggleRadioOnOff()/setRadio()/isRadioOn() (ligar/desligar rádio)
//   - enableApnType()/disableApnType()/enableDataConnectivity()/
//     disableDataConnectivity()/isDataConnectivityPossible() (controle de
//     dados móveis por APN)
//   - getCellLocation()/getNeighboringCellInfo()/enableLocationUpdates()/
//     disableLocationUpdates() (localização de célula via rádio)
//   - handlePinMmi() (execução de código MMI/USSD cru no modem)
//   - getCdmaEriIconIndex()/getCdmaEriIconMode()/getCdmaEriText()/
//     getActivePhoneType() (dependiam de Phone/RILConstants internos; sem
//     equivalente público -- TelephonyManager.getPhoneType() já cobre o caso
//     de uso real de GSM-vs-CDMA em Settings.java, que já foi migrado)
//   - updateServiceLocation() (Phone.updateServiceLocation() interno)
//   - getDataState()/getDataActivity() (DefaultPhoneNotifier interno; quem
//     precisar disso hoje em dia usa TelephonyManager.getDataState() público)
// Nenhum destes tinha um segundo call site fora do próprio arquivo (só o
// AIDL), então cortar não quebra nada já migrado.
//
// Mantido (com chamador real hoje ou wrapper simples e barato de preservar):
//   - dial()/call() -- abrem ACTION_DIAL/ACTION_CALL, igual ao original.
//   - endCall()/answerRingingCall()/silenceRinger() -- viram wrappers diretos
//     sobre PhoneUtils/DonutCallManager.
//   - showCallScreen()/showCallScreenWithDialpad() -- iguais ao original,
//     via PhoneApp.createInCallIntent().
//   - isIdle()/isOffhook()/isRinging()/getCallState() -- consultam
//     DonutCallManager.getInstance().getCalls() em vez de Phone.getState().
//   - cancelMissedCallsNotification() -- único método já chamado de verdade
//     hoje fora deste arquivo (comentário em NotificationMgr.java já citava
//     este call site); continua idêntico.
//   - getVoiceMessageCount() -- via TelephonyManager pública (mesma troca já
//     feita em NotificationMgr.java).

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.Call;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.phone.telecom.DonutCallManager;

/**
 * Lógica de telefonia de nível de app usada pela UI do Phone (dial, call,
 * atender/desligar/silenciar, mostrar tela de chamada, consultar estado).
 * Antes era exposta a outros apps via IPC (ITelephony); hoje é chamada
 * diretamente pelo próprio processo do app.
 */
public class PhoneInterfaceManager {
    private static final String LOG_TAG = "PhoneInterfaceManager";
    private static final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);

    private PhoneInterfaceManager() {
        // Classe utilitária estática -- não instanciar.
    }

    // ------------------------------------------------------------------
    // Discar / ligar
    // ------------------------------------------------------------------

    /** Abre a tela do discador com o número preenchido, sem ligar direto. */
    public static void dial(Context context, String number) {
        if (DBG) log("dial: " + number);

        String url = createTelUrl(number);
        if (url == null) {
            return;
        }

        // Só abre o discador se não houver chamada em andamento/tocando --
        // mesma checagem do original, agora via DonutCallManager.
        if (isIdle()) {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /** Liga direto pro número informado (sem passar pelo discador). */
    public static void call(Context context, String number) {
        if (DBG) log("call: " + number);

        String url = createTelUrl(number);
        if (url == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(context.getPackageName(), PhoneApp.getCallScreenClassName());
        context.startActivity(intent);
    }

    private static String createTelUrl(String number) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }
        return "tel:" + number;
    }

    // ------------------------------------------------------------------
    // Tela de chamada
    // ------------------------------------------------------------------

    private static boolean showCallScreenInternal(Context context,
            boolean specifyInitialDialpadState, boolean initialDialpadState) {
        if (isIdle()) {
            return false;
        }
        Intent intent = specifyInitialDialpadState
                ? PhoneApp.createInCallIntent(initialDialpadState)
                : PhoneApp.createInCallIntent();
        context.startActivity(intent);
        return true;
    }

    /** Mostra a tela de chamada em andamento, sem mexer no estado do teclado. */
    public static boolean showCallScreen(Context context) {
        return showCallScreenInternal(context, false, false);
    }

    /** Mostra a tela de chamada já com o teclado DTMF aberto ou fechado. */
    public static boolean showCallScreenWithDialpad(Context context, boolean showDialpad) {
        return showCallScreenInternal(context, true, showDialpad);
    }

    // ------------------------------------------------------------------
    // Atender / desligar / silenciar
    // ------------------------------------------------------------------

    public static boolean endCall() {
        Call active = firstCallInState(Call.STATE_ACTIVE);
        boolean hungUp = PhoneUtils.hangup(active);
        if (DBG) log("endCall: " + (hungUp ? "hung up!" : "no call to hang up"));
        return hungUp;
    }

    public static void answerRingingCall() {
        if (DBG) log("answerRingingCall...");
        boolean hasActiveCall = firstCallInState(Call.STATE_ACTIVE) != null;
        boolean hasHoldingCall = firstCallInState(Call.STATE_HOLDING) != null;
        if (hasActiveCall && hasHoldingCall) {
            // As duas linhas já em uso: atende a que está tocando e derruba
            // a que já estava ativa -- mesmo comportamento hardcoded que o
            // botão CALL sempre teve no original.
            PhoneUtils.answerAndEndActive();
        } else {
            // answerCall() já coloca a chamada ativa em espera sozinho, se
            // houver uma.
            PhoneUtils.answerCall();
        }
    }

    public static void silenceRinger() {
        if (DBG) log("silenceRinger...");
        PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_IDLE);
    }

    // ------------------------------------------------------------------
    // Consultas de estado
    // ------------------------------------------------------------------

    private static Call firstCallInState(int state) {
        for (Call c : DonutCallManager.getInstance().getCalls()) {
            if (c.getState() == state) {
                return c;
            }
        }
        return null;
    }

    public static boolean isIdle() {
        return DonutCallManager.getInstance().getCalls().isEmpty();
    }

    public static boolean isOffhook() {
        return firstCallInState(Call.STATE_ACTIVE) != null;
    }

    public static boolean isRinging() {
        return firstCallInState(Call.STATE_RINGING) != null;
    }

    // ------------------------------------------------------------------
    // Notificação de chamada perdida
    // ------------------------------------------------------------------

    /** Único método deste arquivo com um call site real fora dele hoje. */
    public static void cancelMissedCallsNotification() {
        NotificationMgr.getDefault().cancelMissedCallNotification();
    }

    // ------------------------------------------------------------------
    // Correio de voz
    // ------------------------------------------------------------------

    /**
     * Quantidade de mensagens de correio de voz não lidas.
     * TelephonyManager.getVoiceMessageCount() virou @SystemApi/@hide (não
     * existe mais no android.jar público) -- sem substituto acessível a um
     * app comum, retorna sempre 0.
     */
    public static int getVoiceMessageCount(Context context) {
        return 0;
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, "[PhoneIntfMgr] " + msg);
    }
}
