#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Corrige o crash ao discar um número:

    java.lang.SecurityException: Permission Denial: not allowed to send
    broadcast android.intent.action.NEW_OUTGOING_CALL from pid=... uid=...

CAUSA
-----
OutgoingCallBroadcaster.onCreate() tentava mandar o broadcast
ACTION_NEW_OUTGOING_CALL na mão (sendOrderedBroadcast) e abrir a
InCallScreen ele mesmo. Isso funcionava no Android 1.6 original porque o
Phone era dono do rádio. No Android moderno, ACTION_NEW_OUTGOING_CALL é um
"protected broadcast": só o próprio sistema (dentro do Telecom framework)
tem permissão de enviá-lo, mesmo segurando android.permission.
PROCESS_OUTGOING_CALLS. Qualquer app comum que tenta mandar esse broadcast
na mão recebe SecurityException e crasha -- exatamente o que estava
acontecendo.

CORREÇÃO (2 arquivos, mesma causa raiz)
----------------------------------------
1) phone/src/main/java/com/android/phone/OutgoingCallBroadcaster.java
   Em vez de broadcast manual + abrir a InCallScreen na mão, delega a
   chamada pro TelecomManager#placeCall(Uri, Bundle) -- API pública. É o
   próprio sistema quem cuida do NEW_OUTGOING_CALL internamente e disca de
   verdade pela rede/SIM.

2) phone/src/main/java/com/android/phone/telecom/DonutInCallService.java
   Sem o OutgoingCallBroadcaster abrindo mais a InCallScreen na mão,
   nada mais no projeto fazia isso -- a chamada seria discada, mas
   nenhuma tela apareceria. Este script adiciona o startActivity(InCallScreen)
   dentro de onCallAdded(), que é o ponto oficial da API InCallService pra
   abrir a UI própria quando o sistema registra uma chamada nova.

USO
---
    python3 corrige_crash_discagem.py [caminho/do/projeto]

Se o caminho não for passado, assume o diretório atual. O script procura
automaticamente a pasta "phone" dentro do caminho informado (ou dentro de
"PhoneDialerDonut", se existir).

Idempotente: pode rodar mais de uma vez sem quebrar nada -- se o arquivo já
estiver corrigido, o script avisa e não mexe em nada.
"""

import sys
import os


def find_project_root(start):
    """Acha a pasta que contém 'phone/src/main/java/com/android/phone'."""
    candidates = [
        start,
        os.path.join(start, "PhoneDialerDonut"),
    ]
    for base in candidates:
        marker = os.path.join(
            base, "phone", "src", "main", "java", "com", "android", "phone"
        )
        if os.path.isdir(marker):
            return base
    return None


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def apply_patch(path, replacements, label):
    """Aplica uma lista de (old, new) em sequência. Cada 'old' precisa
    aparecer exatamente uma vez no arquivo."""
    if not os.path.isfile(path):
        print(f"  [ERRO] Arquivo não encontrado: {path}")
        return False

    content = read(path)
    original_content = content
    already_fixed_marker = "TelecomManager telecomManager"

    if label == "OutgoingCallBroadcaster" and already_fixed_marker in content:
        print(f"  [OK] {label}: já estava corrigido, nada a fazer.")
        return True

    if label == "DonutInCallService" and "InCallScreen.class" in content:
        print(f"  [OK] {label}: já estava corrigido, nada a fazer.")
        return True

    for old, new in replacements:
        count = content.count(old)
        if count == 0:
            print(f"  [ERRO] {label}: trecho esperado não encontrado no arquivo.")
            print("         O arquivo pode já ter sido modificado de outra forma.")
            print("         Nenhuma alteração foi salva neste arquivo.")
            return False
        if count > 1:
            print(f"  [ERRO] {label}: trecho encontrado mais de uma vez ({count}x),")
            print("         correção abortada por segurança para não aplicar no lugar errado.")
            return False
        content = content.replace(old, new, 1)

    if content == original_content:
        print(f"  [OK] {label}: nenhuma mudança necessária.")
        return True

    write(path, content)
    print(f"  [OK] {label}: corrigido com sucesso -> {path}")
    return True


def patch_outgoing_call_broadcaster(phone_java_dir):
    path = os.path.join(phone_java_dir, "OutgoingCallBroadcaster.java")

    replacements = [
        # 1) import do TelecomManager
        (
            "import com.android.phone.compat.TelephonyIntentsCompat;\n"
            "import android.os.Bundle;\n"
            "import android.telephony.PhoneNumberUtils;\n",
            "import com.android.phone.compat.TelephonyIntentsCompat;\n"
            "import android.os.Bundle;\n"
            "import android.telecom.TelecomManager;\n"
            "import android.telephony.PhoneNumberUtils;\n",
        ),
        # 2) remove a constante PERMISSION (não é mais usada)
        (
            "    private static final String PERMISSION = android.Manifest.permission.PROCESS_OUTGOING_CALLS;\n"
            "    private static final String TAG = \"OutgoingCallBroadcaster\";",
            "    private static final String TAG = \"OutgoingCallBroadcaster\";",
        ),
        # 3) remove a variável callNow (não é mais usada) e as duas atribuições
        (
            "        final boolean emergencyNumber =\n"
            "                (number != null) && PhoneNumberUtils.isEmergencyNumber(number);\n"
            "\n"
            "        boolean callNow;\n"
            "\n"
            "        if (getClass().getName().equals(intent.getComponent().getClassName())) {",
            "        final boolean emergencyNumber =\n"
            "                (number != null) && PhoneNumberUtils.isEmergencyNumber(number);\n"
            "\n"
            "        if (getClass().getName().equals(intent.getComponent().getClassName())) {",
        ),
        (
            "                finish();\n"
            "                return;\n"
            "            }\n"
            "            callNow = false;\n"
            "        } else if (TelephonyIntentsCompat.ACTION_CALL_EMERGENCY.equals(action)) {",
            "                finish();\n"
            "                return;\n"
            "            }\n"
            "        } else if (TelephonyIntentsCompat.ACTION_CALL_EMERGENCY.equals(action)) {",
        ),
        (
            "                finish();\n"
            "                return;\n"
            "            }\n"
            "            callNow = true;\n"
            "        } else {",
            "                finish();\n"
            "                return;\n"
            "            }\n"
            "        } else {",
        ),
        # 4) o núcleo do bug: troca broadcast manual + startActivity manual
        #    por TelecomManager#placeCall
        (
            "        PhoneApp.getInstance().wakeUpScreen();\n"
            "        \n"
            "        /* If number is null, we're probably trying to call a non-existent voicemail number or\n"
            "         * something else fishy.  Whatever the problem, there's no number, so there's no point\n"
            "         * in allowing apps to modify the number. */\n"
            "        if (number == null) callNow = true;\n"
            "\n"
            "        if (callNow) {\n"
            "            intent.setClass(this, InCallScreen.class);\n"
            "            startActivity(intent);\n"
            "        }\n"
            "\n"
            "        Intent broadcastIntent = new Intent(Intent.ACTION_NEW_OUTGOING_CALL);\n"
            "        if (number != null) broadcastIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, number);\n"
            "        broadcastIntent.putExtra(EXTRA_ALREADY_CALLED, callNow);\n"
            "        broadcastIntent.putExtra(EXTRA_ORIGINAL_URI, intent.getData().toString());\n"
            "        if (LOGV) Log.v(TAG, \"Broadcasting intent \" + broadcastIntent + \".\");\n"
            "        sendOrderedBroadcast(broadcastIntent, PERMISSION, null, null,\n"
            "                             Activity.RESULT_OK, number, null);\n"
            "\n"
            "        finish();\n"
            "    }\n"
            "\n"
            "}",
            "        PhoneApp.getInstance().wakeUpScreen();\n"
            "\n"
            "        // Entrega a chamada pro Telecom do sistema. Ele que cuida do\n"
            "        // NEW_OUTGOING_CALL (protegido, só o sistema pode mandar) e do\n"
            "        // disque de verdade; a InCallScreen é aberta depois, quando o\n"
            "        // DonutInCallService recebe onCallAdded pra essa chamada.\n"
            "        TelecomManager telecomManager =\n"
            "                (TelecomManager) getSystemService(TELECOM_SERVICE);\n"
            "        if (telecomManager != null) {\n"
            "            telecomManager.placeCall(intent.getData(), intent.getExtras());\n"
            "        } else {\n"
            "            Log.e(TAG, \"TelecomManager indisponível, não foi possível originar a chamada.\");\n"
            "        }\n"
            "\n"
            "        finish();\n"
            "    }\n"
            "\n"
            "}",
        ),
    ]

    print("Corrigindo OutgoingCallBroadcaster.java (o crash em si)...")
    return apply_patch(path, replacements, "OutgoingCallBroadcaster")


def patch_donut_in_call_service(phone_java_dir):
    path = os.path.join(phone_java_dir, "telecom", "DonutInCallService.java")

    replacements = [
        (
            "import android.telecom.Call;\n"
            "import android.telecom.CallAudioState;\n"
            "import android.telecom.InCallService;\n"
            "import android.util.Log;\n"
            "\n"
            "public class DonutInCallService extends InCallService {",
            "import android.content.Intent;\n"
            "import android.telecom.Call;\n"
            "import android.telecom.CallAudioState;\n"
            "import android.telecom.InCallService;\n"
            "import android.util.Log;\n"
            "\n"
            "import com.android.phone.InCallScreen;\n"
            "\n"
            "public class DonutInCallService extends InCallService {",
        ),
        (
            "        Log.i(TAG, \"onCallAdded: \" + call);\n"
            "        call.registerCallback(mCallCallback);\n"
            "        DonutCallManager.getInstance().onCallAdded(call);\n"
            "    }",
            "        Log.i(TAG, \"onCallAdded: \" + call);\n"
            "        call.registerCallback(mCallCallback);\n"
            "        DonutCallManager.getInstance().onCallAdded(call);\n"
            "\n"
            "        // CORREÇÃO: nada mais no projeto abria a InCallScreen depois que o\n"
            "        // OutgoingCallBroadcaster parou de fazer isso na mão (era ele quem\n"
            "        // chamava startActivity(InCallScreen) antes de crashar no broadcast\n"
            "        // proibido). Sem isto aqui, a chamada até seria discada de verdade\n"
            "        // pelo sistema, mas nenhuma tela apareceria. Este é o ponto oficial\n"
            "        // do InCallService pra abrir a UI própria (documentado pela própria\n"
            "        // API): toda vez que o sistema registra uma chamada nova -- discada\n"
            "        // por nós, recebida, ou já em andamento -- abrimos a tela clássica.\n"
            "        // InCallScreen é singleInstance (ver AndroidManifest), então chamar\n"
            "        // startActivity de novo com uma instância já em primeiro plano só\n"
            "        // traz ela pra frente, não recria nem duplica.\n"
            "        Intent intent = new Intent(this, InCallScreen.class);\n"
            "        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);\n"
            "        startActivity(intent);\n"
            "    }",
        ),
    ]

    print("Corrigindo DonutInCallService.java (abrir a tela de chamada)...")
    return apply_patch(path, replacements, "DonutInCallService")


def main():
    start = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    start = os.path.abspath(start)

    root = find_project_root(start)
    if root is None:
        print(f"[ERRO] Não encontrei a pasta 'phone/src/main/java/com/android/phone'")
        print(f"       a partir de: {start}")
        print("       Rode o script de dentro da pasta do projeto, ou passe o")
        print("       caminho do projeto como argumento:")
        print("       python3 corrige_crash_discagem.py /caminho/para/PhoneDialerDonut")
        sys.exit(1)

    phone_java_dir = os.path.join(root, "phone", "src", "main", "java", "com", "android", "phone")
    print(f"Projeto encontrado em: {root}\n")

    ok1 = patch_outgoing_call_broadcaster(phone_java_dir)
    ok2 = patch_donut_in_call_service(phone_java_dir)

    print()
    if ok1 and ok2:
        print("Tudo certo. Recompile o módulo phone (./gradlew :phone:assembleDebug)")
        print("e teste discar um número de novo.")
    else:
        print("Alguma correção não pôde ser aplicada automaticamente -- veja os")
        print("erros acima. Nenhum arquivo com erro foi alterado.")
        sys.exit(1)


if __name__ == "__main__":
    main()
