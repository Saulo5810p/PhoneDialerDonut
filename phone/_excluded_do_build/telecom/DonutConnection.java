package com.android.phone.telecom;

// Representa UMA chamada (equivalente conceitual ao antigo
// com.android.internal.telephony.Connection, mas na API pública). O sistema
// Telecom é quem de fato controla o rádio/RIL por baixo — esta classe só recebe
// os comandos do usuário (atender/recusar/desligar/DTMF/mudo) vindos da nossa UI
// e repassa pro sistema através dos métodos set*() da superclasse, que por sua
// vez notificam o InCallService (DonutInCallService) e qualquer outro app
// observando a chamada.

import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.util.Log;

public class DonutConnection extends Connection {

    private static final String TAG = "DonutConnection";

    @Override
    public void onAnswer() {
        Log.i(TAG, "onAnswer");
        setActive();
        DonutCallManager.getInstance().onConnectionStateChanged(this);
    }

    @Override
    public void onReject() {
        Log.i(TAG, "onReject");
        setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        destroy();
        DonutCallManager.getInstance().onConnectionRemoved(this);
    }

    @Override
    public void onDisconnect() {
        Log.i(TAG, "onDisconnect");
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();
        DonutCallManager.getInstance().onConnectionRemoved(this);
    }

    @Override
    public void onHold() {
        Log.i(TAG, "onHold");
        setOnHold();
        DonutCallManager.getInstance().onConnectionStateChanged(this);
    }

    @Override
    public void onUnhold() {
        Log.i(TAG, "onUnhold");
        setActive();
        DonutCallManager.getInstance().onConnectionStateChanged(this);
    }

    @Override
    public void onPlayDtmfTone(char c) {
        Log.i(TAG, "onPlayDtmfTone: " + c);
        // O tom em si é gerado/enviado pelo sistema Telecom — aqui só repassamos.
    }

    @Override
    public void onStopDtmfTone() {
        Log.i(TAG, "onStopDtmfTone");
    }

    @Override
    public void onCallAudioStateChanged(android.telecom.CallAudioState state) {
        DonutCallManager.getInstance().onCallAudioStateChanged(state);
    }

    /**
     * Chamado pela nossa UI (CallCard/InCallScreen) quando o usuário aperta
     * "desligar" — não confundir com onDisconnect(), que é o SISTEMA nos
     * avisando. Aqui é o caminho inverso: nós avisamos o sistema.
     */
    public void hangUpFromUi() {
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();
        DonutCallManager.getInstance().onConnectionRemoved(this);
    }
}
