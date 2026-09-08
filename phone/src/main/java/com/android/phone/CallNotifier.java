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

// REESCRITO PARA A ROTA A.
//
// O CallNotifier original (1193 linhas) era um Handler que reagia a mensagens
// postadas pelo motor interno (com.android.internal.telephony.{Phone,Call,
// Connection}) via AsyncResult — "chamada nova chegou", "desconectou", "call
// waiting CDMA", "tom de sinal CDMA", "MWI mudou", etc. Praticamente todo esse
// mecanismo de registro de evento (registerForNewRingingConnection etc.) não
// existe mais pra apps normais.
//
// Nesta versão, CallNotifier vira um DonutCallManager.Listener: reage aos
// mesmos eventos de estado, só que vindos do android.telecom.Call via
// DonutCallManager em vez do motor interno. O escopo foi reduzido ao que a UI
// atual (InCallScreen) realmente precisa: tocar/parar o toque de chamada
// recebida. Funcionalidades específicas de CDMA (call waiting tone, signal
// info tone, display info) foram cortadas — mesma decisão já aplicada em
// InCallScreen/CallCard/PhoneUtils: o Android moderno não expõe esses eventos
// de RIL pra apps normais, e a distinção GSM/CDMA não faz mais sentido aqui.
//
// TODO (pendente, não bloqueia o caminho principal): sendBatteryLow() e
// sendMwiChangedDelayed() ficaram como stubs — quem os chama (PhoneApp,
// NotificationMgr) ainda não foi migrado nesta sessão.

import android.content.Context;
import android.telecom.Call;
import android.util.Log;

import com.android.phone.telecom.DonutCallManager;

public class CallNotifier implements DonutCallManager.Listener {
    private static final String LOG_TAG = "CallNotifier";
    private static final boolean DBG = false;

    private final Context mContext;
    private final Ringer mRinger;

    /** A chamada que está tocando no momento (null se nenhuma). */
    private Call mRingingCall;

    public CallNotifier(Context context, Ringer ringer) {
        mContext = context;
        mRinger = ringer;
        DonutCallManager.getInstance().addListener(this);
    }

    @Override
    public void onCallAdded(Call call) {
        if (call.getState() == Call.STATE_RINGING) {
            startRinging(call);
        }
    }

    @Override
    public void onCallStateChanged(Call call) {
        if (call == mRingingCall && call.getState() != Call.STATE_RINGING) {
            // A chamada que tocava foi atendida, recusada, ou terminou de outro
            // jeito — para o toque.
            stopRinging();
        } else if (call.getState() == Call.STATE_RINGING && mRingingCall == null) {
            startRinging(call);
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        if (call == mRingingCall) {
            stopRinging();
        }
        // Registro de chamada perdida/atendida no CallLog já é feito pelo
        // próprio sistema Telecom automaticamente pra chamadas que ele
        // gerencia — não precisamos mais escrever no CallLog manualmente
        // (era isso que showMissedCallNotification/CallLog.Calls.addCall
        // faziam na versão original).
    }

    @Override
    public void onAudioStateChanged(android.telecom.CallAudioState state) {
        // Nada a fazer aqui por enquanto — CallCard/InCallScreen já observam
        // isso diretamente via DonutCallManager.
    }

    private void startRinging(Call call) {
        if (DBG) log("startRinging: " + call);
        mRingingCall = call;
        mRinger.ring();
    }

    private void stopRinging() {
        if (DBG) log("stopRinging");
        mRingingCall = null;
        mRinger.stopRing();
    }

    boolean isRinging() {
        return mRingingCall != null;
    }

    /** Para o toque sem recusar a chamada (ex: usuário apertou volume). */
    void silenceRinger() {
        if (mRingingCall != null) {
            mRinger.stopRing();
        }
    }

    /** TODO: aviso sonoro de bateria fraca durante ligação — ainda não religado. */
    void sendBatteryLow() {
        if (DBG) log("sendBatteryLow (stub, ainda não religado)");
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
