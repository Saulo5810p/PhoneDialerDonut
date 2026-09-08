/*
 * Copyright (C) 2008 The Android Open Source Project
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
 * ADAPTADO — roaming CDMA e modo de subscription (Phone.setCdmaRoamingPreference/
 * setCdmaSubscription/queryCdmaRoamingPreference) são signature-only, sem
 * equivalente público, igual ao resto do grupo PIN/rede. Além disso a tela
 * inteira só era mostrada quando mPhone.getPhoneName().equals("CDMA") -- CDMA
 * já foi desligado pelas operadoras principais, então essa tela dificilmente
 * chega a aparecer num aparelho real hoje. Layout mantido; os dois
 * ListPreference agora só mostram a mensagem de "não faço milagre" ao serem
 * tocados. O terceiro item (cell broadcast) continua indo pro CellBroadcastSms
 * via intent do próprio cdma_options.xml, sem precisar de mudança aqui.
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
public class CdmaOptions extends PreferenceActivity {

    private static final String BUTTON_CDMA_ROAMING_KEY = "cdma_roaming_mode_key";
    private static final String BUTTON_CDMA_SUBSCRIPTION_KEY = "subscription_key";

    private Preference mButtonCdmaRoam;
    private Preference mButtonCdmaSubscription;

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mButtonCdmaRoam || preference == mButtonCdmaSubscription) {
            // Roaming/subscription CDMA exige API interna signature-only
            // -- ver nota no topo do arquivo.
            NotPortedYet.show(this);
            return true;
        }
        // outros itens (ex.: cell broadcast) seguem pelo fluxo normal de intent do XML.
        return false;
    }

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.cdma_options);

        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonCdmaRoam = prefSet.findPreference(BUTTON_CDMA_ROAMING_KEY);
        mButtonCdmaSubscription = prefSet.findPreference(BUTTON_CDMA_SUBSCRIPTION_KEY);
    }
}
