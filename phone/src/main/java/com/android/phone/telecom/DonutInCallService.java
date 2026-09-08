package com.android.phone.telecom;

// A PEÇA REAL do "ícone que volta pra tela de chamada": quando o app está
// registrado como InCallService padrão do sistema (isso é feito no
// AndroidManifest, não aqui — precisa da permissão BIND_INCALL_SERVICE + o
// usuário escolher o app como discador padrão), o Android chama onCallAdded()
// automaticamente sempre que existe uma chamada ativa, e é exatamente esse
// momento que o sistema também habilita o ícone do launcher a reabrir a tela de
// chamada — comportamento nativo do InCallService, não precisa ser programado
// à mão.
//
// Esta classe só faz a ponte: recebe os eventos do sistema e repassa pro
// DonutCallManager, que por sua vez a UI antiga (CallNotifier/InCallScreen)
// escuta sem precisar conhecer android.telecom diretamente.

import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.util.Log;

public class DonutInCallService extends InCallService {

    private static final String TAG = "DonutInCallService";

    private final Call.Callback mCallCallback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            DonutCallManager.getInstance().onCallStateChanged(call);
        }
    };

    @Override
    public void onCallAdded(Call call) {
        Log.i(TAG, "onCallAdded: " + call);
        call.registerCallback(mCallCallback);
        DonutCallManager.getInstance().onCallAdded(call);
    }

    @Override
    public void onCallRemoved(Call call) {
        Log.i(TAG, "onCallRemoved: " + call);
        call.unregisterCallback(mCallCallback);
        DonutCallManager.getInstance().onCallRemoved(call);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        DonutCallManager.getInstance().onCallAudioStateChanged(audioState);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DonutCallManager.getInstance().setInCallService(this);
    }

    @Override
    public void onDestroy() {
        DonutCallManager.getInstance().setInCallService(null);
        super.onDestroy();
    }
}
