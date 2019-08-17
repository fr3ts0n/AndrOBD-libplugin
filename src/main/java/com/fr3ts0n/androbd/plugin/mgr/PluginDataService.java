package com.fr3ts0n.androbd.plugin.mgr;

import android.content.Context;
import android.content.Intent;

import com.fr3ts0n.androbd.plugin.Plugin;
import com.fr3ts0n.androbd.plugin.PluginInfo;

/**
 * PluginDataService
 * <p>
 * This class implements a host-sided plugin to allow data reception
 * from other plugins.
 * The handling of received data is forwarded to a external data receiver
 * which must be provided by the application side.
 */
public class PluginDataService
        extends Plugin
        implements Plugin.DataReceiver
{
    static final PluginInfo myInfo = new PluginInfo("AndrOBD",
                                                    PluginDataService.class,
                                                    "AndrOBD host data receiver",
                                                    "Copyright (C) 2019 by fr3ts0n",
                                                    "GPLV3+",
                                                    "https://github.com/fr3ts0n/AndrOBD");

    /**
     * external data receiver (i.e. application)
     */
    static Plugin.DataReceiver dataReceiver = null;

    @Override
    public PluginInfo getPluginInfo()
    {
        return myInfo;
    }

    /**
     * external data receiver
     */
    public Plugin.DataReceiver getDataReceiver()
    {
        return dataReceiver;
    }

    /**
     * Register external data receiver
     *
     * @param dataReceiver External data receiver to handle data updates
     */
    public void setDataReceiver(Plugin.DataReceiver dataReceiver)
    {
        PluginDataService.dataReceiver = dataReceiver;
    }

    @Override
    public void handleIdentify(Context context, Intent intent)
    {
        // intentionally ignore IDENTIFY request
    }

    @Override
    public void onDataListUpdate(String csvString)
    {
        // forward update to registered data receiver
        if (getDataReceiver() != null)
        {
            getDataReceiver().onDataListUpdate(csvString);
        }
    }

    @Override
    public void onDataUpdate(String key, String value)
    {
        // forward update to registered data receiver
        if (getDataReceiver() != null)
        {
            getDataReceiver().onDataUpdate(key, value);
        }
    }
}
