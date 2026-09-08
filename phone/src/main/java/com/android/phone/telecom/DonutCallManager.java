package com.android.phone.telecom;

// PONTE entre android.telecom.Call (objeto do SISTEMA, que representa uma
// chamada real do SIM/rádio — gerenciada pelo próprio Android, não por nós) e a
// UI de 2009 que estamos preservando (CallNotifier, InCallScreen, CallCard).
// Esses três arquivos originais esperavam objetos
// com.android.internal.telephony.Call/Connection/Phone, que não existem mais pra
// apps normais. Este singleton republica os eventos do Call do sistema num
// formato simples (Listener) que a UI antiga consegue observar.
//
// Quem alimenta este singleton é o DonutInCallService: toda vez que o sistema
// registra uma chamada nova (recebida, discada por nós, ou por qualquer outro
// caminho — inclusive chamadas do discador padrão do sistema, se o nosso app
// não for o InCallService ativo), o InCallService recebe onCallAdded/
// onCallRemoved e repassa pra cá.

import android.telecom.Call;
import android.telecom.CallAudioState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DonutCallManager {

    public interface Listener {
        /** Nova chamada apareceu — pode ser recebida, discada por nós, ou já em andamento. */
        void onCallAdded(Call call);
        /** Estado da chamada mudou (tocando -> ativa -> em espera -> etc). */
        void onCallStateChanged(Call call);
        /** Chamada terminou/foi removida da lista de chamadas ativas. */
        void onCallRemoved(Call call);
        /** Estado de áudio mudou (viva-voz, fone, mudo). */
        void onAudioStateChanged(CallAudioState state);
    }

    private static final DonutCallManager sInstance = new DonutCallManager();

    public static DonutCallManager getInstance() {
        return sInstance;
    }

    private final List<Listener> mListeners = new CopyOnWriteArrayList<Listener>();
    private final List<Call> mCalls = new ArrayList<Call>();
    private CallAudioState mAudioState;
    private android.telecom.InCallService mInCallService;

    private DonutCallManager() {
    }

    public void addListener(Listener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    /** Devolve a lista de chamadas ativas no momento (0, 1 ou 2 — chamada em espera). */
    public List<Call> getCalls() {
        return new ArrayList<Call>(mCalls);
    }

    /** A primeira chamada ativa (a que a InCallScreen mostra em foreground). */
    public Call getForegroundCall() {
        return mCalls.isEmpty() ? null : mCalls.get(0);
    }

    public CallAudioState getAudioState() {
        return mAudioState;
    }

    /** Usado pela UI (CallCard) pra mandar comandos de áudio (mudo/viva-voz/fone). */
    public void setInCallService(android.telecom.InCallService service) {
        mInCallService = service;
        if (service != null) {
            mAudioState = service.getCallAudioState();
        }
    }

    public void setMuted(boolean muted) {
        if (mInCallService != null) {
            mInCallService.setMuted(muted);
        }
    }

    public void setSpeakerphoneOn(boolean on) {
        if (mInCallService != null) {
            mInCallService.setAudioRoute(on
                    ? android.telecom.CallAudioState.ROUTE_SPEAKER
                    : android.telecom.CallAudioState.ROUTE_EARPIECE);
        }
    }

    /**
     * Substitui o antigo BluetoothHandsfree/BluetoothHeadsetService (stack
     * de AT commands rodado pelo próprio app -- android.bluetooth.
     * AtCommandHandler/AtParser/HeadsetBase/ScoSocket/BluetoothAudioGateway,
     * tudo removido do SDK público faz tempo). Hoje quem faz o papel de
     * Audio Gateway HFP é o próprio serviço de Bluetooth do sistema; um app
     * comum só pede pra rotear o áudio da chamada pra lá, via
     * CallAudioState.ROUTE_BLUETOOTH (API pública) -- o pareamento, o SCO e
     * o protocolo AT ficam por conta do sistema, "de graça", do mesmo jeito
     * que o roteamento de viva-voz.
     */
    public boolean isBluetoothRouteAvailable() {
        return mAudioState != null
                && (mAudioState.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0;
    }

    public boolean isBluetoothRouteOn() {
        return mAudioState != null && mAudioState.getRoute() == CallAudioState.ROUTE_BLUETOOTH;
    }

    public void setBluetoothOn(boolean on) {
        if (mInCallService != null) {
            mInCallService.setAudioRoute(on
                    ? CallAudioState.ROUTE_BLUETOOTH
                    : CallAudioState.ROUTE_EARPIECE);
        }
    }

    void onCallAdded(Call call) {
        mCalls.add(call);
        for (Listener l : mListeners) {
            l.onCallAdded(call);
        }
    }

    void onCallRemoved(Call call) {
        mCalls.remove(call);
        for (Listener l : mListeners) {
            l.onCallRemoved(call);
        }
    }

    void onCallStateChanged(Call call) {
        for (Listener l : mListeners) {
            l.onCallStateChanged(call);
        }
    }

    void onCallAudioStateChanged(CallAudioState state) {
        mAudioState = state;
        for (Listener l : mListeners) {
            l.onAudioStateChanged(state);
        }
    }
}
