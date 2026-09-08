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
 *
 * ============================================================================
 * ADAPTADO — configuração de cell broadcast SMS (CDMA) inteira dependia de
 * Phone.activateCellBroadcastSms/getCellBroadcastSmsConfig/
 * setCellBroadcastSmsConfig, indo direto pro RIL via struct C interna
 * (RILConstants) -- signature-only, sem equivalente público, e hoje esse
 * tipo de canal já é gerenciado pelo app dedicado CellBroadcastReceiver do
 * próprio sistema. Layout original mantido (todas as ~30 categorias
 * continuam aparecendo, xml/cell_broadcast_sms.xml intocado), mas como todos
 * os itens já são persistent="false", basta interceptar os cliques: em vez
 * de tentar configurar o RIL, cada toque mostra a mensagem de "não faço
 * milagre".
 * ============================================================================
 */

package com.android.phone;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import com.android.phone.compat.NotPortedYet;

/**
 * List of Phone-specific settings screens.
 */
public class CellBroadcastSms extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        NotPortedYet.show(this);
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        NotPortedYet.show(this);
        // não deixa a mudança ser persistida/aplicada -- não há nada real
        // por trás dela hoje.
        return false;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.cell_broadcast_sms);
    }
}
