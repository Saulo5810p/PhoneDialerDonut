#!/usr/bin/env python3
"""
Corrige o erro de build:

    mergeDebugResources FAILED
    javax.xml.stream.XMLStreamException: ParseError ...
    Message: The string "--" is not permitted within comments.

Causa: um comentário em incall_screen.xml (adicionado numa correção
anterior) tinha "--" (hífen duplo) dentro do texto. XML proíbe "--"
dentro do CORPO de um comentário -- só pode aparecer na abertura
"<!--" e no fechamento "-->" dele.

O que este script faz: varre todo res/ dos módulos phone/ e contacts/,
acha qualquer comentário <!-- ... --> cujo conteúdo tenha "--" (ou mais
hífens seguidos) e troca por um hífen simples "-", preservando o resto
do texto. Roda de novo sem problema (é seguro rodar mais de uma vez).

Uso: salve este arquivo na RAIZ do projeto (onde ficam as pastas
phone/ e contacts/) e rode:

    python3 corrige_comentario_xml.py
"""

import os
import re
import sys

MODULOS = ["phone", "contacts"]

# Acha comentários XML inteiros, incluindo os que quebram em várias linhas.
COMMENT_RE = re.compile(r"<!--(.*?)-->", re.DOTALL)


def corrige_conteudo(texto):
    def _fix(match):
        interior = match.group(1)
        # Troca qualquer sequência de 2+ hífens por um hífen só,
        # em qualquer lugar dentro do corpo do comentário.
        interior_corrigido = re.sub(r"-{2,}", "-", interior)
        return "<!--" + interior_corrigido + "-->"

    return COMMENT_RE.sub(_fix, texto)


def main():
    raiz = os.getcwd()
    arquivos_corrigidos = []

    for modulo in MODULOS:
        res_dir = os.path.join(raiz, modulo, "src", "main", "res")
        if not os.path.isdir(res_dir):
            continue
        for dirpath, _dirnames, filenames in os.walk(res_dir):
            for nome in filenames:
                if not nome.endswith(".xml"):
                    continue
                caminho = os.path.join(dirpath, nome)
                try:
                    with open(caminho, "r", encoding="utf-8") as f:
                        original = f.read()
                except UnicodeDecodeError:
                    continue

                corrigido = corrige_conteudo(original)
                if corrigido != original:
                    with open(caminho, "w", encoding="utf-8") as f:
                        f.write(corrigido)
                    arquivos_corrigidos.append(os.path.relpath(caminho, raiz))

    if not arquivos_corrigidos:
        print("Nenhum '--' inválido encontrado dentro de comentários.")
        print("Se o build ainda falhar com o mesmo erro, confira se você")
        print("rodou este script na pasta raiz do projeto (a que tem")
        print("phone/ e contacts/ dentro).")
        sys.exit(0)

    print("Corrigido(s):")
    for caminho in arquivos_corrigidos:
        print("  -", caminho)
    print()
    print("Pronto. Rode de novo: ./gradlew phone:assembleDebug")


if __name__ == "__main__":
    main()
