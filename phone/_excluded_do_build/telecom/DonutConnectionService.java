package com.android.phone.telecom;

// ATENÇÃO: esta classe NÃO é usada no caminho principal do app (chamadas pelo SIM
// normal do aparelho). Um ConnectionService só é necessário quando o PRÓPRIO app é
// dono de um jeito de fazer ligação (ex: VoIP) — pra usar o rádio/SIM do sistema,
// a peça certa é o InCallService (veja DonutInCallService), que recebe os objetos
// Call de qualquer chamada ativa gerenciada pelo sistema, venha de onde vier.
// Mantida aqui como referência/base caso um dia se queira um "phone account"
// próprio (ex: chamada de teste, VoIP), mas não é registrada no manifest.
//
// Todo o resto do comentário original abaixo continua válido conceitualmente —
// só a decisão de USAR isso no fluxo de chamada por SIM que mudou.
//
// No Android 1.6 original, o PhoneApp falava direto com o motor de telefonia do
// sistema (RIL/GSM/CDMA) através de com.android.internal.telephony.Phone/Call/
// Connection — classes que vivem no processo do sistema e são inacessíveis a
// qualquer app normal em qualquer versão moderna do Android.
//
// A partir da API 23, o Android expõe android.telecom.ConnectionService: uma API
// PÚBLICA pela qual um app se registra junto ao sistema como "provedor de
// chamadas". O próprio sistema (o serviço "Telecom", dono de verdade do RIL/GSM/
// CDMA por baixo) é quem gerencia o estado real da chamada — GERENCIA:
//   - chamada recebida chega aqui via onCreateIncomingConnection()
//   - chamada discada por nós chega aqui via onCreateOutgoingConnection()
//   - o objeto Connection que devolvemos é a "alça" que usamos pra sinalizar
//     atender/recusar/desligar/mudo/viva-voz — e é a partir dele que a UI antiga
//     (CallCard/InCallScreen) vai ler o estado da chamada, através do
//     DonutCallManager (ponte que criamos pra não precisar reescrever a UI toda).
//
// Ou seja: não reimplementamos GSM/CDMA/RIL — o sistema já faz isso. Nosso app só
// participa do ciclo de vida da chamada através dessa API pública.

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

public class DonutConnectionService extends ConnectionService {

    private static final String TAG = "DonutConnectionService";

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        Uri address = request.getAddress();
        Log.i(TAG, "onCreateOutgoingConnection: " + address);

        DonutConnection connection = new DonutConnection();
        connection.setAddress(address, android.telecom.TelecomManager.PRESENTATION_ALLOWED);
        connection.setDialing();
        connection.setAudioModeIsVoip(false);
        connection.setConnectionCapabilities(
                Connection.CAPABILITY_MUTE
                        | Connection.CAPABILITY_SUPPORT_HOLD
                        | Connection.CAPABILITY_HOLD);

        // Regista a conexão na ponte compartilhada — é daqui que CallNotifier/
        // InCallScreen/CallCard (a UI que preservamos do 2009) vão ler o estado.
        DonutCallManager.getInstance().onOutgoingConnectionCreated(connection, address);

        return connection;
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        Log.w(TAG, "onCreateOutgoingConnectionFailed: " + request.getAddress());
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        Uri address = request.getAddress();
        Log.i(TAG, "onCreateIncomingConnection: " + address);

        DonutConnection connection = new DonutConnection();
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        connection.setRinging();
        connection.setConnectionCapabilities(
                Connection.CAPABILITY_MUTE
                        | Connection.CAPABILITY_SUPPORT_HOLD
                        | Connection.CAPABILITY_HOLD);

        DonutCallManager.getInstance().onIncomingConnectionCreated(connection, address);

        return connection;
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerAccount,
            ConnectionRequest request) {
        Log.w(TAG, "onCreateIncomingConnectionFailed: " + request.getAddress());
    }
}
