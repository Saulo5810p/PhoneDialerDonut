#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Corrige o crash ao discar/atender uma chamada:

    java.lang.SecurityException: Requires DISABLE_KEYGUARD permission
        at android.app.KeyguardManager$KeyguardLock.disableKeyguard(...)
        at com.android.phone.PhoneApp.disableKeyguard(PhoneApp.java:281)
        at com.android.phone.InCallScreen.onResume(InCallScreen.java:330)

CAUSA
-----
InCallScreen.onResume() chama PhoneApp.disableKeyguard(), que por sua vez
chama KeyguardManager.KeyguardLock.disableKeyguard(). Esse método exige a
permissão signature/system DISABLE_KEYGUARD -- ela nem está mais declarada
no AndroidManifest.xml do projeto (foi removida de propósito numa rodada
anterior, por ser letra morta pra um app normal). Sem a permissão, o método
lança SecurityException direto do WindowManagerService e derruba a
activity assim que ela tenta voltar pro primeiro plano -- exatamente ao
discar ou atender.

CORREÇÃO (2 arquivos, mesma causa raiz)
----------------------------------------
1) phone/src/main/java/com/android/phone/InCallScreen.java
   onCreate() passa a usar a API pública moderna pra aparecer sobre a tela
   bloqueada e acender a tela: Activity.setShowWhenLocked(true) +
   setTurnScreenOn(true) a partir do Android 8.1 (API 27), e as flags de
   janela equivalentes (FLAG_SHOW_WHEN_LOCKED / FLAG_TURN_SCREEN_ON /
   FLAG_KEEP_SCREEN_ON) nas versões mais antigas suportadas (minSdk 23).

2) phone/src/main/java/com/android/phone/PhoneApp.java
   disableKeyguard()/reenableKeyguard() ganham um try/catch em volta da
   chamada ao KeyguardLock: se algum fabricante ainda liberar a permissão,
   continua funcionando; se não (o caso normal hoje), a SecurityException é
   engolida em vez de derrubar o app. A correção de verdade é a do arquivo
   1 -- isto aqui é só uma rede de segurança pra esse método nunca mais
   crashar sozinho.

USO
---
    python3 corrige_crash_keyguard.py [caminho/do/projeto]

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


def apply_patch(path, replacements, label, already_fixed_marker):
    """Aplica uma lista de (old, new) em sequência. Cada 'old' precisa
    aparecer exatamente uma vez no arquivo."""
    if not os.path.isfile(path):
        print(f"  [ERRO] Arquivo não encontrado: {path}")
        return False

    content = read(path)
    original_content = content

    if already_fixed_marker in content:
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


def patch_incall_screen(phone_java_dir):
    path = os.path.join(phone_java_dir, "InCallScreen.java")

    replacements = [
        (
            "        requestWindowFeature(Window.FEATURE_NO_TITLE);\n"
            "\n"
            "        // Inflate everything in incall_screen.xml and add it to the screen.",
            "        requestWindowFeature(Window.FEATURE_NO_TITLE);\n"
            "\n"
            "        // Faz a tela de chamada aparecer por cima da tela bloqueada e\n"
            "        // acender a tela, sem depender da permissão signature-only\n"
            "        // DISABLE_KEYGUARD (era o que causava o crash SecurityException ao\n"
            "        // discar/atender). setShowWhenLocked/setTurnScreenOn são a API\n"
            "        // pública equivalente a partir do Android 8.1 (API 27); nas\n"
            "        // versões anteriores (minSdk 23) o mesmo efeito é obtido com as\n"
            "        // flags de janela clássicas.\n"
            "        if (android.os.Build.VERSION.SDK_INT >= 27) {\n"
            "            setShowWhenLocked(true);\n"
            "            setTurnScreenOn(true);\n"
            "        } else {\n"
            "            getWindow().addFlags(\n"
            "                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED\n"
            "                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON\n"
            "                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);\n"
            "        }\n"
            "\n"
            "        // Inflate everything in incall_screen.xml and add it to the screen.",
        ),
    ]

    print("Corrigindo InCallScreen.java (mostrar sobre a tela bloqueada sem DISABLE_KEYGUARD)...")
    return apply_patch(path, replacements, "InCallScreen", already_fixed_marker="setShowWhenLocked(true)")


def patch_phone_app(phone_java_dir):
    path = os.path.join(phone_java_dir, "PhoneApp.java")

    replacements = [
        (
            "    void disableKeyguard() {\n"
            "        if (mKeyguardLock != null) {\n"
            "            mKeyguardLock.disableKeyguard();\n"
            "        }\n"
            "    }\n"
            "\n"
            "    void reenableKeyguard() {\n"
            "        if (mKeyguardLock != null) {\n"
            "            mKeyguardLock.reenableKeyguard();\n"
            "        }\n"
            "    }",
            "    void disableKeyguard() {\n"
            "        // KeyguardLock.disableKeyguard() exige a permissão signature/system\n"
            "        // DISABLE_KEYGUARD, que não é mais concedível a apps normais --\n"
            "        // chamar isso sem a permissão derruba o processo com\n"
            "        // SecurityException (era o crash ao atender/discar). O caminho que\n"
            "        // realmente funciona em app normal é\n"
            "        // Activity.setShowWhenLocked(true)/setTurnScreenOn(true) (ou as\n"
            "        // flags de janela equivalentes em API < 27), já aplicado em\n"
            "        // InCallScreen.onCreate(). Mantido como no-op protegido por\n"
            "        // try/catch só por segurança, caso algum fabricante ainda libere.\n"
            "        if (mKeyguardLock != null) {\n"
            "            try {\n"
            "                mKeyguardLock.disableKeyguard();\n"
            "            } catch (SecurityException e) {\n"
            "                if (DBG) Log.d(LOG_TAG, \"disableKeyguard: sem permissão, ignorando (esperado)\");\n"
            "            }\n"
            "        }\n"
            "    }\n"
            "\n"
            "    void reenableKeyguard() {\n"
            "        if (mKeyguardLock != null) {\n"
            "            try {\n"
            "                mKeyguardLock.reenableKeyguard();\n"
            "            } catch (SecurityException e) {\n"
            "                if (DBG) Log.d(LOG_TAG, \"reenableKeyguard: sem permissão, ignorando (esperado)\");\n"
            "            }\n"
            "        }\n"
            "    }",
        ),
    ]

    print("Corrigindo PhoneApp.java (nunca mais crashar em disableKeyguard/reenableKeyguard)...")
    return apply_patch(path, replacements, "PhoneApp", already_fixed_marker="sem permissão, ignorando (esperado)")


def main():
    start = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    start = os.path.abspath(start)

    root = find_project_root(start)
    if root is None:
        print(f"[ERRO] Não encontrei a pasta 'phone/src/main/java/com/android/phone'")
        print(f"       a partir de: {start}")
        print("       Rode o script de dentro da pasta do projeto, ou passe o")
        print("       caminho do projeto como argumento:")
        print("       python3 corrige_crash_keyguard.py /caminho/para/PhoneDialerDonut")
        sys.exit(1)

    phone_java_dir = os.path.join(root, "phone", "src", "main", "java", "com", "android", "phone")
    print(f"Projeto encontrado em: {root}\n")

    ok1 = patch_incall_screen(phone_java_dir)
    ok2 = patch_phone_app(phone_java_dir)

    print()
    if ok1 and ok2:
        print("Tudo certo. Recompile o módulo phone (./gradlew :phone:assembleDebug)")
        print("e teste discar/atender um número de novo.")
    else:
        print("Alguma correção não pôde ser aplicada automaticamente -- veja os")
        print("erros acima. Nenhum arquivo com erro foi alterado.")
        sys.exit(1)


if __name__ == "__main__":
    main()
