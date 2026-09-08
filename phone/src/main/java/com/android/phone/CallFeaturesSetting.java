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
 * ADAPTADO (Rota A / DonutCallManager) — mudanças em relação ao 1.6 original:
 *
 * 1. Não existe mais com.android.internal.telephony.{CommandsInterface,
 *    CallForwardInfo,Phone,PhoneFactory} — a versão original usava
 *    CommandsInterface pra consultar e programar diretamente os serviços
 *    suplementares (encaminhamento de chamada, identificador de chamadas,
 *    chamada em espera) no modem via RIL. Um app comum não tem acesso a
 *    isso em nenhuma versão do Android.
 * 2. A tela continua existindo com os MESMOS campos (mesmo
 *    res/xml/call_feature_setting.xml), mas o "motor" agora só faz duas
 *    coisas honestas:
 *      a) guarda localmente (SharedPreferences) o último valor que o
 *         usuário configurou nesta tela, só pra UI continuar coerente
 *         entre uma abertura e outra;
 *      b) tenta aplicar a mudança discando o código MMI/USSD padrão GSM
 *         correspondente (ex.: **21*numero#, #21#, *43#, #43#, *31#, #31#)
 *         via Intent.ACTION_CALL — exatamente como qualquer discador
 *         comum faz. A operadora/rede é quem processa; este app NÃO lê
 *         nem confirma o resultado (isso exigiria CommandsInterface).
 * 3. Distinção CDMA removida (TTY, Voice Privacy, DTMF tone type): são
 *    ajustes de sistema que exigem WRITE_SECURE_SETTINGS/permissão de
 *    app privilegiado — fora do alcance de um app comum. Este arquivo só
 *    infla res/xml/call_feature_setting.xml (o layout "GSM-like"),
 *    mesmo padrão já adotado no InCallMenu.
 * 4. Número de correio de voz: sem API pública de ESCRITA pra apps
 *    comuns (quem grava isso normalmente é a operadora/SIM). O valor
 *    padrão sugerido no diálogo vem de TelephonyManager.getVoiceMailNumber()
 *    (leitura pública); o que o usuário salva aqui fica só como
 *    substituição local, usada pelo restante do app (atalho de discagem
 *    de voicemail), documentado — não reprograma o SIM/operadora.
 * 5. Fixed Dialing Numbers (FDN): mantido como PreferenceScreen que abre
 *    FdnSetting (arquivo separado, migração própria) — inalterado aqui.
 * ============================================================================
 */

package com.android.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * "Call settings" UI, versão adaptada: sem motor RIL interno, os serviços
 * suplementares (encaminhamento, chamada em espera, identificador) são
 * aplicados por discagem MMI padrão (best-effort), não por consulta/
 * programação direta ao modem.
 */
public class CallFeaturesSetting extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener,
        EditPhoneNumberPreference.OnDialogClosedListener,
        EditPhoneNumberPreference.GetDefaultNumberListener {

    private static final String LOG_TAG = "CallFeaturesSetting";
    private static final boolean DBG = true;

    /** Intent action usada por quem quer abrir esta tela já na aba de voicemail. */
    public static final String ACTION_ADD_VOICEMAIL =
            "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";

    // Chaves dos preferences, iguais ao res/xml/call_feature_setting.xml original.
    private static final String BUTTON_VOICEMAIL_KEY = "button_voicemail_key";
    private static final String BUTTON_FDN_KEY        = "button_fdn_key";
    private static final String BUTTON_GSM_MORE_EXPAND_KEY = "button_gsm_more_expand_key";
    private static final String BUTTON_CF_EXPAND_KEY  = "button_cf_expand_key";
    private static final String BUTTON_CLIR_KEY       = "button_clir_key";
    private static final String BUTTON_CW_KEY         = "button_cw_key";
    private static final String BUTTON_CFU_KEY        = "button_cfu_key";
    private static final String BUTTON_CFB_KEY        = "button_cfb_key";
    private static final String BUTTON_CFNRY_KEY      = "button_cfnry_key";
    private static final String BUTTON_CFNRC_KEY      = "button_cfnrc_key";

    // Motivo do encaminhamento de chamada — equivalente local aos antigos
    // CommandsInterface.CF_REASON_*, só pra montar o código MMI certo.
    private static final int REASON_UNCONDITIONAL = 0;
    private static final int REASON_BUSY = 1;
    private static final int REASON_NO_REPLY = 2;
    private static final int REASON_NOT_REACHABLE = 3;

    private static final String PREFS_NAME = "call_features_setting";
    private static final String PREF_VOICEMAIL_NUMBER = "voicemail_number";
    private static final String PREF_CLIR_VALUE = "clir_value";
    private static final String PREF_CW_ENABLED = "cw_enabled";
    private static final String PREF_CF_ENABLED_PREFIX = "cf_enabled_";
    private static final String PREF_CF_NUMBER_PREFIX = "cf_number_";

    private SharedPreferences mPrefs;

    private EditPhoneNumberPreference mVoicemailPref;
    private PreferenceScreen mFdnPref;
    private ListPreference mClirPref;
    private CheckBoxPreference mCwPref;
    private EditPhoneNumberPreference mCfuPref;
    private EditPhoneNumberPreference mCfbPref;
    private EditPhoneNumberPreference mCfnryPref;
    private EditPhoneNumberPreference mCfnrcPref;

    /** Mapa preference -> motivo de encaminhamento, pra montar o MMI certo ao fechar o diálogo. */
    private final Map<Preference, Integer> mCfReasons = new HashMap<Preference, Integer>();

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.call_feature_setting);

        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        PreferenceScreen prefSet = getPreferenceScreen();

        mFdnPref = (PreferenceScreen) prefSet.findPreference(BUTTON_FDN_KEY);
        if (mFdnPref != null) {
            Intent fdnIntent = new Intent(Intent.ACTION_MAIN);
            fdnIntent.setClassName(this, FdnSetting.class.getName());
            mFdnPref.setIntent(fdnIntent);
        }

        mVoicemailPref = (EditPhoneNumberPreference) prefSet.findPreference(BUTTON_VOICEMAIL_KEY);
        if (mVoicemailPref != null) {
            mVoicemailPref.setParentActivity(this, 0, this);
            mVoicemailPref.setDialogOnClosedListener(this);
            mVoicemailPref.setDialogTitle(R.string.voicemail_settings_number_label);
            String savedVm = mPrefs.getString(PREF_VOICEMAIL_NUMBER, null);
            if (!TextUtils.isEmpty(savedVm)) {
                mVoicemailPref.setPhoneNumber(savedVm);
            }
        }

        mClirPref = (ListPreference) prefSet.findPreference(BUTTON_CLIR_KEY);
        if (mClirPref != null) {
            String savedClir = mPrefs.getString(PREF_CLIR_VALUE, "DEFAULT");
            mClirPref.setValue(savedClir);
            mClirPref.setOnPreferenceChangeListener(this);
        }

        mCwPref = (CheckBoxPreference) prefSet.findPreference(BUTTON_CW_KEY);
        if (mCwPref != null) {
            mCwPref.setChecked(mPrefs.getBoolean(PREF_CW_ENABLED, false));
            mCwPref.setOnPreferenceChangeListener(this);
        }

        mCfuPref = bindCallForwardingPref(prefSet, BUTTON_CFU_KEY, REASON_UNCONDITIONAL,
                R.string.messageCFU);
        mCfbPref = bindCallForwardingPref(prefSet, BUTTON_CFB_KEY, REASON_BUSY,
                R.string.messageCFB);
        mCfnryPref = bindCallForwardingPref(prefSet, BUTTON_CFNRY_KEY, REASON_NO_REPLY,
                R.string.messageCFNRy);
        mCfnrcPref = bindCallForwardingPref(prefSet, BUTTON_CFNRC_KEY, REASON_NOT_REACHABLE,
                R.string.messageCFNRc);

        if (ACTION_ADD_VOICEMAIL.equals(getIntent().getAction()) && mVoicemailPref != null) {
            mVoicemailPref.showPhoneNumberDialog();
        }
    }

    private EditPhoneNumberPreference bindCallForwardingPref(PreferenceScreen prefSet,
            String key, int reason, int messageResId) {
        EditPhoneNumberPreference pref =
                (EditPhoneNumberPreference) prefSet.findPreference(key);
        if (pref == null) return null;

        pref.setParentActivity(this, reason, this);
        pref.setDialogOnClosedListener(this);
        pref.setDialogTitle(R.string.labelCF);
        pref.setDialogMessage(messageResId);

        boolean enabled = mPrefs.getBoolean(PREF_CF_ENABLED_PREFIX + key, false);
        String number = mPrefs.getString(PREF_CF_NUMBER_PREFIX + key, "");
        pref.setToggled(enabled);
        pref.setPhoneNumber(number);

        mCfReasons.put(pref, reason);
        return pref;
    }

    /**
     * EditPhoneNumberPreference.GetDefaultNumberListener — só é usado
     * pela preference de voicemail, pra sugerir o número da operadora
     * (se o sistema souber) quando o usuário nunca configurou um local.
     */
    @Override
    public String onGetDefaultNumber(EditPhoneNumberPreference preference) {
        if (preference != mVoicemailPref) return null;
        String saved = mPrefs.getString(PREF_VOICEMAIL_NUMBER, null);
        if (!TextUtils.isEmpty(saved)) return null; // já tem valor local, não sobrescreve

        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            String carrierVm = (tm != null) ? tm.getVoiceMailNumber() : null;
            return TextUtils.isEmpty(carrierVm) ? null : carrierVm;
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * EditPhoneNumberPreference.OnDialogClosedListener — chamado quando
     * qualquer um dos diálogos (voicemail ou encaminhamento) é fechado.
     */
    @Override
    public void onDialogClosed(EditPhoneNumberPreference preference, int buttonClicked) {
        if (buttonClicked == DialogInterface.BUTTON2) {
            if (DBG) log("onDialogClosed: cancelado (" + preference.getKey() + ")");
            return;
        }

        if (preference == mVoicemailPref) {
            String number = preference.getPhoneNumber();
            mPrefs.edit().putString(PREF_VOICEMAIL_NUMBER, number).apply();
            Toast.makeText(this, R.string.vm_changed, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer reason = mCfReasons.get(preference);
        if (reason == null) {
            Log.w(LOG_TAG, "onDialogClosed: preference desconhecida: " + preference.getKey());
            return;
        }

        boolean enabled = preference.isToggled();
        String number = preference.getPhoneNumber();

        mPrefs.edit()
                .putBoolean(PREF_CF_ENABLED_PREFIX + preference.getKey(), enabled)
                .putString(PREF_CF_NUMBER_PREFIX + preference.getKey(), number)
                .apply();

        String mmi = buildCallForwardingMmi(reason, enabled, number);
        dialMmiBestEffort(mmi);
    }

    /**
     * Preference.OnPreferenceChangeListener — usado pela ListPreference de
     * identificador de chamadas (CLIR) e pelo CheckBoxPreference de
     * chamada em espera (CW), já que nenhum dos dois passa pelo fluxo de
     * diálogo do EditPhoneNumberPreference.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mClirPref) {
            String value = String.valueOf(newValue);
            mPrefs.edit().putString(PREF_CLIR_VALUE, value).apply();
            if ("HIDE".equals(value)) {
                dialMmiBestEffort("*31#");
            } else if ("SHOW".equals(value)) {
                dialMmiBestEffort("#31#");
            }
            // "DEFAULT" não dispara MMI nenhum — deixa a operadora decidir.
            return true;
        }

        if (preference == mCwPref) {
            boolean enabled = Boolean.TRUE.equals(newValue);
            mPrefs.edit().putBoolean(PREF_CW_ENABLED, enabled).apply();
            dialMmiBestEffort(enabled ? "*43#" : "#43#");
            return true;
        }

        return true;
    }

    /**
     * Monta o código MMI/USSD padrão GSM equivalente ao pedido de
     * encaminhamento de chamada. Retorna null se não houver número válido
     * para uma ativação (nesse caso não há o que discar).
     */
    private static String buildCallForwardingMmi(int reason, boolean enable, String number) {
        String servicePrefix;
        switch (reason) {
            case REASON_BUSY:          servicePrefix = "67"; break;
            case REASON_NO_REPLY:      servicePrefix = "61"; break;
            case REASON_NOT_REACHABLE: servicePrefix = "62"; break;
            case REASON_UNCONDITIONAL:
            default:                   servicePrefix = "21"; break;
        }

        if (enable) {
            String target = PhoneNumberUtils.stripSeparators(number);
            if (TextUtils.isEmpty(target)) {
                return null;
            }
            return "**" + servicePrefix + "*" + target + "#";
        } else {
            return "##" + servicePrefix + "#";
        }
    }

    /**
     * Disca um código MMI/USSD como se fosse um número normal — é assim
     * que qualquer discador comum (sem privilégio de sistema) aciona
     * serviços suplementares: quem interpreta o código é a rede/modem, e
     * este app não recebe nem mostra a resposta (isso é responsabilidade
     * de quem estiver registrado como app de chamada padrão no momento).
     */
    private void dialMmiBestEffort(String mmiCode) {
        if (TextUtils.isEmpty(mmiCode)) {
            Toast.makeText(this, R.string.no_change, Toast.LENGTH_SHORT).show();
            return;
        }
        if (DBG) log("dialMmiBestEffort: " + mmiCode);
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", mmiCode, null));
            startActivity(intent);
        } catch (SecurityException e) {
            Log.w(LOG_TAG, "dialMmiBestEffort: sem permissão CALL_PHONE: " + e);
            new AlertDialog.Builder(this)
                    .setMessage(R.string.exception_error)
                    .setPositiveButton(R.string.close_dialog, null)
                    .show();
        } catch (Exception e) {
            Log.w(LOG_TAG, "dialMmiBestEffort: falha ao discar " + mmiCode + ": " + e);
        }
    }

    private static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
