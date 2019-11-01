/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.util.ArrayMap;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;

import java.util.List;
import java.util.Map;

/**
 * This class manages cached copies of all the MMS configuration for each subscription ID.
 * A subscription ID loosely corresponds to a particular SIM. See the
 * {@link android.telephony.SubscriptionManager} for more details.
 *
 */
public class MmsConfigManager {
    private static volatile MmsConfigManager sInstance = new MmsConfigManager();

    public static MmsConfigManager getInstance() {
        return sInstance;
    }

    // Map the various subIds to their corresponding MmsConfigs.
    private final Map<Integer, Bundle> mSubIdConfigMap = new ArrayMap<Integer, Bundle>();
    private Context mContext;
    private SubscriptionManager mSubscriptionManager;

    /**
     * This receiver listens for changes made to SubInfoRecords and for a broadcast telling us
     * the TelephonyManager has loaded the information needed in order to get the mcc/mnc's for
     * each subscription Id. When either of these broadcasts are received, we rebuild the
     * MmsConfig table.
     *
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtil.i("MmsConfigManager receiver action: " + action);
            if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED) ||
                    action.equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                loadInBackground();
            }
        }
    };

    private final OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            loadInBackground();
        }
    };


    public void init(final Context context) {
        mContext = context;
        mSubscriptionManager = SubscriptionManager.from(context);

        // TODO: When this object "finishes" we should unregister.
        final IntentFilter intentFilterLoaded =
                new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilterLoaded.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        context.registerReceiver(mReceiver, intentFilterLoaded);

        // TODO: When this object "finishes" we should unregister by invoking
        // SubscriptionManager.getInstance(mContext).unregister(mOnSubscriptionsChangedListener);
        // This is not strictly necessary because it will be unregistered if the
        // notification fails but it is good form.

        // Register for SubscriptionInfo list changes which is guaranteed
        // to invoke onSubscriptionsChanged the first time.
        SubscriptionManager.from(mContext).addOnSubscriptionsChangedListener(
                mOnSubscriptionsChangedListener);
    }

    private void loadInBackground() {
        // TODO (ywen) - AsyncTask to avoid creating a new thread?
        new Thread() {
            @Override
            public void run() {
                Configuration configuration = mContext.getResources().getConfiguration();
                // Always put the mnc/mcc in the log so we can tell which mms_config.xml
                // was loaded.
                LogUtil.i("MmsConfigManager loads in background mcc/mnc: " +
                        configuration.mcc + "/" + configuration.mnc);
                load(mContext);
            }
        }.start();
    }

    /**
     * Find and return the MMS config for a particular subscription id.
     *
     * @param subId Subscription id of the desired MMS config bundle
     * @return MMS config bundle for the particular subscription id. This function can return null
     *         if the MMS config cannot be found or if this function is called before the
     *         TelephonyManager has set up the SIMs, or if loadInBackground is still spawning a
     *         thread after a recent LISTEN_SUBSCRIPTION_INFO_LIST_CHANGED event.
     */
    public Bundle getMmsConfigBySubId(int subId) {
        Bundle mmsConfig;
        synchronized(mSubIdConfigMap) {
            mmsConfig = mSubIdConfigMap.get(subId);
        }
        LogUtil.i("mms config for sub " + subId + ": " + mmsConfig);
        // Return a copy so that callers can mutate it.
        if (mmsConfig != null) {
          return new Bundle(mmsConfig);
        }
        return null;
    }

    /**
     * Filters a bundle to only contain MMS config variables.
     *
     * This is for use with bundles returned by {@link CarrierConfigManager} which contain MMS
     * config and unrelated config. It is assumed that all MMS_CONFIG_* keys are present in the
     * supplied bundle.
     *
     * @param config a Bundle that contains MMS config variables and possibly more.
     * @return a new Bundle that only contains the MMS_CONFIG_* keys defined above.
     * @hide
     */
    private static Bundle getMmsConfig(BaseBundle config) {
        Bundle filtered = new Bundle();
        filtered.putBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID,
                config.getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID));
        filtered.putBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED,
                config.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED));
        filtered.putBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED,
                config.getBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED));
        filtered.putBoolean(SmsManager.MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED,
                config.getBoolean(SmsManager.MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED));
        filtered.putBoolean(SmsManager.MMS_CONFIG_ALIAS_ENABLED,
                config.getBoolean(SmsManager.MMS_CONFIG_ALIAS_ENABLED));
        filtered.putBoolean(SmsManager.MMS_CONFIG_ALLOW_ATTACH_AUDIO,
                config.getBoolean(SmsManager.MMS_CONFIG_ALLOW_ATTACH_AUDIO));
        filtered.putBoolean(SmsManager.MMS_CONFIG_MULTIPART_SMS_ENABLED,
                config.getBoolean(SmsManager.MMS_CONFIG_MULTIPART_SMS_ENABLED));
        filtered.putBoolean(SmsManager.MMS_CONFIG_SMS_DELIVERY_REPORT_ENABLED,
                config.getBoolean(SmsManager.MMS_CONFIG_SMS_DELIVERY_REPORT_ENABLED));
        filtered.putBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION,
                config.getBoolean(SmsManager.MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION));
        filtered.putBoolean(SmsManager.MMS_CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES,
                config.getBoolean(SmsManager.MMS_CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES));
        filtered.putBoolean(SmsManager.MMS_CONFIG_MMS_READ_REPORT_ENABLED,
                config.getBoolean(SmsManager.MMS_CONFIG_MMS_READ_REPORT_ENABLED));
        filtered.putBoolean(SmsManager.MMS_CONFIG_MMS_DELIVERY_REPORT_ENABLED,
                config.getBoolean(SmsManager.MMS_CONFIG_MMS_DELIVERY_REPORT_ENABLED));
        filtered.putBoolean(SmsManager.MMS_CONFIG_CLOSE_CONNECTION,
                config.getBoolean(SmsManager.MMS_CONFIG_CLOSE_CONNECTION));
        filtered.putInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE,
                config.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE));
        filtered.putInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH,
                config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH));
        filtered.putInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT,
                config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT));
        filtered.putInt(SmsManager.MMS_CONFIG_RECIPIENT_LIMIT,
                config.getInt(SmsManager.MMS_CONFIG_RECIPIENT_LIMIT));
        filtered.putInt(SmsManager.MMS_CONFIG_ALIAS_MIN_CHARS,
                config.getInt(SmsManager.MMS_CONFIG_ALIAS_MIN_CHARS));
        filtered.putInt(SmsManager.MMS_CONFIG_ALIAS_MAX_CHARS,
                config.getInt(SmsManager.MMS_CONFIG_ALIAS_MAX_CHARS));
        filtered.putInt(SmsManager.MMS_CONFIG_SMS_TO_MMS_TEXT_THRESHOLD,
                config.getInt(SmsManager.MMS_CONFIG_SMS_TO_MMS_TEXT_THRESHOLD));
        filtered.putInt(SmsManager.MMS_CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD,
                config.getInt(SmsManager.MMS_CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD));
        filtered.putInt(SmsManager.MMS_CONFIG_MESSAGE_TEXT_MAX_SIZE,
                config.getInt(SmsManager.MMS_CONFIG_MESSAGE_TEXT_MAX_SIZE));
        filtered.putInt(SmsManager.MMS_CONFIG_SUBJECT_MAX_LENGTH,
                config.getInt(SmsManager.MMS_CONFIG_SUBJECT_MAX_LENGTH));
        filtered.putInt(SmsManager.MMS_CONFIG_HTTP_SOCKET_TIMEOUT,
                config.getInt(SmsManager.MMS_CONFIG_HTTP_SOCKET_TIMEOUT));
        filtered.putString(SmsManager.MMS_CONFIG_UA_PROF_TAG_NAME,
                config.getString(SmsManager.MMS_CONFIG_UA_PROF_TAG_NAME));
        filtered.putString(SmsManager.MMS_CONFIG_USER_AGENT,
                config.getString(SmsManager.MMS_CONFIG_USER_AGENT));
        filtered.putString(SmsManager.MMS_CONFIG_UA_PROF_URL,
                config.getString(SmsManager.MMS_CONFIG_UA_PROF_URL));
        filtered.putString(SmsManager.MMS_CONFIG_HTTP_PARAMS,
                config.getString(SmsManager.MMS_CONFIG_HTTP_PARAMS));
        filtered.putString(SmsManager.MMS_CONFIG_EMAIL_GATEWAY_NUMBER,
                config.getString(SmsManager.MMS_CONFIG_EMAIL_GATEWAY_NUMBER));
        filtered.putString(SmsManager.MMS_CONFIG_NAI_SUFFIX,
                config.getString(SmsManager.MMS_CONFIG_NAI_SUFFIX));
        filtered.putBoolean(SmsManager.MMS_CONFIG_SHOW_CELL_BROADCAST_APP_LINKS,
                config.getBoolean(SmsManager.MMS_CONFIG_SHOW_CELL_BROADCAST_APP_LINKS));
        filtered.putBoolean(SmsManager.MMS_CONFIG_SUPPORT_HTTP_CHARSET_HEADER,
                config.getBoolean(SmsManager.MMS_CONFIG_SUPPORT_HTTP_CHARSET_HEADER));
        return filtered;
    }

    /**
     * This loads the MMS config for each active subscription.
     *
     * MMS config is fetched from CarrierConfigManager and filtered to only include MMS config
     * variables. The resulting bundles are stored in mSubIdConfigMap.
     */
    private void load(Context context) {
        List<SubscriptionInfo> subs = mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subs == null || subs.size() < 1) {
            LogUtil.e(" Failed to load mms config: empty getActiveSubInfoList");
            return;
        }
        // Load all the config bundles into a new map and then swap it with the real map to avoid
        // blocking.
        final Map<Integer, Bundle> newConfigMap = new ArrayMap<Integer, Bundle>();
        final CarrierConfigManager configManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        for (SubscriptionInfo sub : subs) {
            final int subId = sub.getSubscriptionId();
            PersistableBundle config = configManager.getConfigForSubId(subId);
            newConfigMap.put(subId, getMmsConfig(config));
        }
        synchronized(mSubIdConfigMap) {
            mSubIdConfigMap.clear();
            mSubIdConfigMap.putAll(newConfigMap);
        }
    }

}
