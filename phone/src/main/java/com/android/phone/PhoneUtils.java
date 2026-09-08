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
// O PhoneUtils original (1727 linhas) fazia a ponte entre a UI e o motor de
// telefonia interno (com.android.internal.telephony.{Phone,Call,Connection}),
// com bastante lógica específica de GSM vs. CDMA e chamadas diretas ao RIL.
// Nada disso é acessível a um app normal, e a maior parte já não é necessária:
// o sistema Telecom (via android.telecom.Call) já resolve o estado da chamada,
// GSM/CDMA, e o roteamento de áudio por baixo. Esta versão é um wrapper fino
// sobre o DonutCallManager, cobrindo só os métodos que a UI (CallCard,
// InCallScreen, DTMFTwelveKeyDialer, InCallMenu) realmente usa.
//
// Note que a maioria dos métodos agora não recebe mais "qual chamada" como
// parâmetro em vários casos (ex: answerCall(), mergeCalls()) — eles operam
// sobre "a chamada tocando"/"a chamada ativa"/"a chamada em espera" que o
// DonutCallManager já sabe encontrar sozinho pela lista de Call ativos.

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.phone.compat.CallerInfo;
import com.android.phone.compat.CallerInfoAsyncQuery;
import com.android.phone.compat.CallerInfoAsyncQuery.OnQueryCompleteListener;
import com.android.phone.telecom.DonutCallManager;

public class PhoneUtils {
    private static final String LOG_TAG = "PhoneUtils";
    private static final boolean DBG = false;

    // Estados "de áudio" simplificados — mantidos como constantes porque a UI
    // antiga referencia PhoneUtils.AUDIO_IDLE etc. diretamente. No modelo antigo
    // controlavam o AudioManager.setMode() manualmente; no Telecom moderno o
    // próprio sistema já cuida do modo de áudio da chamada, então
    // setAudioControlState() vira um no-op documentado — mantido só por
    // compatibilidade de chamada com a UI.
    static final int AUDIO_IDLE = 0;
    static final int AUDIO_RINGING = 1;
    static final int AUDIO_OFFHOOK = 2;

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private static Call findCallInState(int state) {
        for (Call c : DonutCallManager.getInstance().getCalls()) {
            if (c.getState() == state) {
                return c;
            }
        }
        return null;
    }

    private static Call getRingingCall() {
        return findCallInState(Call.STATE_RINGING);
    }

    private static Call getActiveCall() {
        return findCallInState(Call.STATE_ACTIVE);
    }

    private static Call getHoldingCall() {
        return findCallInState(Call.STATE_HOLDING);
    }

    // ------------------------------------------------------------------
    // Atender / desligar / trocar
    // ------------------------------------------------------------------

    static boolean answerCall() {
        Call ringing = getRingingCall();
        if (ringing == null) {
            if (DBG) log("answerCall: no ringing call");
            return false;
        }
        ringing.answer(VideoProfile.STATE_AUDIO_ONLY);
        return true;
    }

    /** Atende a chamada tocando e derruba a que já estava ativa. */
    static boolean answerAndEndActive() {
        Call active = getActiveCall();
        if (active != null) {
            active.disconnect();
        }
        return answerCall();
    }

    static boolean hangup(Call call) {
        if (call == null) {
            return false;
        }
        call.disconnect();
        return true;
    }

    static boolean hangupActiveCall() {
        return hangup(getActiveCall());
    }

    static boolean hangupHoldingCall() {
        return hangup(getHoldingCall());
    }

    static boolean hangupRingingCall() {
        Call ringing = getRingingCall();
        if (ringing == null) {
            return false;
        }
        ringing.reject(false, null);
        return true;
    }

    /** Põe a chamada em espera ativa e retoma a que estava em espera (ou vice-versa). */
    static void switchHoldingAndActive() {
        Call active = getActiveCall();
        Call held = getHoldingCall();
        if (active != null) {
            active.hold();
        }
        if (held != null) {
            held.unhold();
        }
    }

    static boolean mergeCalls() {
        Call active = getActiveCall();
        Call held = getHoldingCall();
        if (active == null || held == null) {
            return false;
        }
        active.conference(held);
        return true;
    }

    /** Tira uma chamada de uma conferência, voltando ela a ser independente. */
    static boolean separateCall(Call call) {
        if (call == null) {
            return false;
        }
        call.splitFromConference();
        return true;
    }

    static boolean isConferenceCall(Call call) {
        if (call == null) {
            return false;
        }
        return call.getChildren() != null && !call.getChildren().isEmpty();
    }

    // ------------------------------------------------------------------
    // Pode fazer X agora? (habilita/desabilita botões da UI)
    // ------------------------------------------------------------------

    static boolean okToAddCall() {
        return DonutCallManager.getInstance().getCalls().size() < 2;
    }

    static boolean okToMergeCalls() {
        return getActiveCall() != null && getHoldingCall() != null;
    }

    static boolean okToSwapCalls() {
        return getHoldingCall() != null;
    }

    // ------------------------------------------------------------------
    // Áudio (mudo / viva-voz)
    // ------------------------------------------------------------------

    static boolean getMute() {
        CallAudioState state = DonutCallManager.getInstance().getAudioState();
        return state != null && state.isMuted();
    }

    static void setMute(boolean muted) {
        DonutCallManager.getInstance().setMuted(muted);
    }

    static boolean isSpeakerOn(Context context) {
        CallAudioState state = DonutCallManager.getInstance().getAudioState();
        return state != null && state.getRoute() == CallAudioState.ROUTE_SPEAKER;
    }

    static void turnOnSpeaker(Context context, boolean flag, boolean store) {
        DonutCallManager.getInstance().setSpeakerphoneOn(flag);
    }

    /** Restaura o áudio pro fone de ouvido normal (usado ao encerrar chamada). */
    static void restoreSpeakerMode(Context context) {
        DonutCallManager.getInstance().setSpeakerphoneOn(false);
    }

    // ------------------------------------------------------------------
    // Bluetooth (roteamento de áudio via sistema -- ver DonutCallManager)
    // ------------------------------------------------------------------

    /** Há um dispositivo Bluetooth de chamada disponível pra rotear áudio agora? */
    static boolean isBluetoothAvailable() {
        return DonutCallManager.getInstance().isBluetoothRouteAvailable();
    }

    /** O áudio da chamada está roteado pro Bluetooth agora? */
    static boolean isBluetoothAudioOn() {
        return DonutCallManager.getInstance().isBluetoothRouteOn();
    }

    /** Liga/desliga o roteamento de áudio pro Bluetooth. */
    static void setBluetoothOn(boolean on) {
        DonutCallManager.getInstance().setBluetoothOn(on);
    }

    /**
     * No modelo antigo controlava o AudioManager.setMode() manualmente conforme
     * o estado da chamada (tocando/em ligação/ocioso). O Telecom moderno já faz
     * esse controle de modo de áudio sozinho — vira no-op, mantido só pra não
     * quebrar os call sites existentes na UI.
     */
    static void setAudioControlState(int state) {
        if (DBG) log("setAudioControlState (no-op no Telecom moderno): " + state);
    }

    /** Wrapper fino sobre AudioManager.setMode() — API pública normal, sem nada interno. */
    static void setAudioMode(Context context, int mode) {
        AudioManager audioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(mode);
        }
    }

    // ------------------------------------------------------------------
    // CallerInfo (nome/foto do contato a partir do número)
    // ------------------------------------------------------------------

    private static String getNumberFromCall(Call call) {
        if (call == null || call.getDetails() == null) {
            return null;
        }
        Uri handle = call.getDetails().getHandle();
        return handle != null ? handle.getSchemeSpecificPart() : null;
    }

    static CallerInfo getCallerInfo(Context context, Call call) {
        String number = getNumberFromCall(call);
        return CallerInfo.getCallerInfo(context, number);
    }

    static void startGetCallerInfo(Context context, Call call, OnQueryCompleteListener listener,
            Object cookie) {
        String number = getNumberFromCall(call);
        CallerInfoAsyncQuery.startQuery(context, number, listener, cookie);
    }

    static String getCompactNameFromCallerInfo(CallerInfo info, Context context) {
        if (info == null) {
            return context.getString(R.string.unknown);
        }
        if (!TextUtils.isEmpty(info.name)) {
            return info.name;
        }
        if (!TextUtils.isEmpty(info.phoneNumber)) {
            return PhoneNumberUtils.formatNumber(info.phoneNumber);
        }
        return context.getString(R.string.unknown);
    }
}
