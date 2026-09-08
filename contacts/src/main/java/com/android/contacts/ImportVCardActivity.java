/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.app.Activity;
import android.os.Bundle;

import com.android.contacts.compat.NotPortedYet;

/**
 * Decisão do Saulo (STATUS-PROJETO.md): esta tela dependia inteiramente de
 * android.syncml.pim.* (parser de VCard interno do Donut), API que nunca foi
 * pública em nenhuma versão do Android — não dá pra portar, nem com root.
 *
 * O ponto de entrada (item de menu "Importar do cartão SD") continua existindo
 * em ContactsListActivity; ao ser acionado, esta Activity só mostra o aviso e
 * fecha, em vez de tentar rodar um parser que não existe mais.
 *
 * A lógica original completa (parser VCard, threads de leitura, diálogos de
 * seleção de arquivo) foi movida pra
 * contacts/_excluded_do_build/ImportVCardActivity.java.original, como referência.
 */
public class ImportVCardActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotPortedYet.show(this);
        finish();
    }
}
