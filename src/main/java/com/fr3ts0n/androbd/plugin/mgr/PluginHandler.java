package com.fr3ts0n.androbd.plugin.mgr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.TextView;

import com.fr3ts0n.androbd.plugin.Plugin;
import com.fr3ts0n.androbd.plugin.PluginInfo;
import com.fr3ts0n.androbd.plugin.R;

/**
 * Plugin handler
 * <p>
 * This class handles a list of detected plugins:
 * - Allow adding / deleting plugin instances
 * - Handle automatic plugin detection
 * - Provide display adapter of current plugin list
 * - Handle sending Intents to individual / all plugins
 */
public class PluginHandler
        extends ArrayAdapter<PluginInfo>
        implements Plugin.DataProvider
{
    /**
     * Plugin servicefor data reception
     */
    private transient PluginDataService svc = new PluginDataService();

    /**
     * layout inflater
     */
    private transient LayoutInflater mInflater;

    /**
     * Application preferences
     */
    private SharedPreferences mPrefs;

    /**
     * the receiver to receive IDENTIFY responses
     */
    private BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.v(toString(), "Broadcast received: " + intent);

            String action = intent.getAction();
            if (Plugin.IDENTIFY.equals(action))
            {
                PluginInfo plugin = new PluginInfo(intent.getExtras());
                Log.i(toString(), "Plugin identified: " + plugin.toString());
                // get preferred enable/disable state from settings
                plugin.enabled = mPrefs.getBoolean(plugin.className, true);
                // add plugin to list
                add(plugin);
                // set current enabled/disabled state (to stop disabled services)
                setPluginEnabled(getPosition(plugin), plugin.enabled);
            }

            intent.setClass(getContext(), PluginDataService.class);
            context.startService(intent);
        }
    };

    /**
     * Constructor
     *  @param context  The current context.
     */
    PluginHandler(Context context)
    {
        this(context, R.layout.plugininfo);
    }

    /**
     * Constructor
     *  @param context  The current context.
     *  @param resource The resource ID for a layout file containing a TextView to use when
     */
    PluginHandler(Context context, int resource)
    {
        super(context, resource);
        // create layout inflater
        mInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        setup();
    }

    /**
     * get external data receiver
     */
    Plugin.DataReceiver getDataReceiver()
    {
        return svc.getDataReceiver();
    }

    /**
     * Set external data receiver component
     *
     * @param dataReceiver external data receiver component
     */
    void setDataReceiver(Plugin.DataReceiver dataReceiver)
    {
        svc.setDataReceiver(dataReceiver);
    }

    /**
     * Set up all components
     * - register receiver
     * - initiate plugin identification
     */
    void setup()
    {
        // register this handler as a receive filter
        IntentFilter flt = new IntentFilter();
        flt.addCategory(Plugin.RESPONSE);
        flt.addAction(Plugin.IDENTIFY);
        flt.addAction(Plugin.DATALIST);
        flt.addAction(Plugin.DATA);
        getContext().registerReceiver(receiver, flt);

        // trigger plugin search
        identifyPlugins();
    }

    /**
     * Clean up all components
     * - close all plugins
     * - unregister receiver
     */
    void cleanup()
    {
        // close all olugins
        closeAllPlugins();

        // unregister receiver
        try
        {
            getContext().unregisterReceiver(receiver);
        } catch (Exception e)
        {
            Log.e(toString(), e.getMessage());
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        PluginInfo info = getItem(position);

        View infoView;
        if (convertView != null)
            infoView = convertView;
        else
            infoView = mInflater.inflate(R.layout.plugininfo, parent, false);

        TextView tv;
        tv = infoView.findViewById(R.id.edName);
        tv.setEnabled(info.enabled);
        tv.setText(info.name);

        tv = infoView.findViewById(R.id.edClass);
        tv.setEnabled(info.enabled);
        tv.setText(info.className);

        tv = infoView.findViewById(R.id.edDescription);
        tv.setEnabled(info.enabled);
        tv.setText(info.description);

        // get feature checkboxes
        CheckBox[] cb =
        {
            infoView.findViewById(R.id.cbConfig),
            infoView.findViewById(R.id.cbAction),
            infoView.findViewById(R.id.cbDataList),
            infoView.findViewById(R.id.cbData),
        };

        // set checkbox checked state based on supported features
        for (int bit = 0; bit < 4; bit++)
        {
            cb[bit].setChecked((info.features & (1 << bit)) != 0);
        }

        // enable / disable buttons based on supported features
        Button btn;
        btn = infoView.findViewById(R.id.btnConfigure);
        btn.setEnabled(info.enabled && (info.features & PluginInfo.FEATURE_CONFIGURE) != 0);

        btn = infoView.findViewById(R.id.btnAction);
        btn.setEnabled(info.enabled && (info.features & PluginInfo.FEATURE_ACTION) != 0);

        infoView.setActivated(info.enabled);

        Switch swEnable = infoView.findViewById(R.id.swEnable);
        swEnable.setChecked(info.enabled);

        return infoView;
    }

    /**
     * enable/disable specified plugin
     *
     * Enabling triggers a action event to the plugin.
     * Disabling stops the plugin service
     *
     * @param position position of plugin within array
     * @param enable flag if to enable (@ref true) / disable (@ref false) the plugin
     */
    private void setPluginEnabled(int position, boolean enable)
    {
        // set enabled state in plugin info
        PluginInfo plugin = getItem(position);
        plugin.enabled = enable;
        // remember this state in settings
        mPrefs.edit().putBoolean(plugin.className, enable).apply();

        // notify about changes to re-trigger display
        notifyDataSetChanged();

        if (enable)
        {
            // initiate plugin action
            triggerAction(position);
        }
        else
        {
            // actively stop plugin service if switched off
            stopPlugin(position);
        }
    }

    /**
     * Send broadcast message to identify installed plugins
     */
    private void identifyPlugins()
    {
        // send broadcast IDENTIFY
        Intent intent = new Intent(Plugin.IDENTIFY);
        intent.addCategory(Plugin.REQUEST);
        intent.putExtras(svc.getPluginInfo().toBundle());
        Log.i(toString(), ">IDENTIFY: " + intent);
        getContext().sendBroadcast(intent);
    }

    /**
     * Stop plugin at specified list position
     *
     * @param position List position of plugin
     */
    private void stopPlugin(int position)
    {
        Intent intent = new Intent();
        PluginInfo plugin = getItem(position);
        intent.setClassName(plugin.packageName, plugin.className);
        Log.i(toString(), "Stop service: " + intent);
        getContext().stopService(intent);
    }

    /**
     * Initiate ACTION of specified plugin
     *
     * This sends a ACTION message to the plugin
     *
     * @param position List position of plugin
     */
    private void triggerAction(int position)
    {
        PluginInfo plugin = getItem(position);
        if (plugin.enabled
                && (plugin.features & PluginInfo.FEATURE_ACTION) != 0)
        {
            Intent intent = new Intent(Plugin.ACTION);
            intent.setClassName(plugin.packageName, plugin.className);
            intent.putExtra(PluginInfo.Field.CLASS.toString(), plugin.className);
            Log.d(toString(), ">ACTION: " + intent);
            getContext().startService(intent);
        }
    }

    /**
     * Initiate configuration dialog of spcified plugin
     *
     * This sends a CONFIGURE message to the plugin
     *
     * @param position List position of plugin
     */
    void triggerConfiguration(int position)
    {
        PluginInfo plugin = getItem(position);
        if (plugin.enabled
                && (plugin.features & PluginInfo.FEATURE_CONFIGURE) != 0)
        {
            Intent intent = new Intent(Plugin.CONFIGURE);
            intent.setClassName(plugin.packageName, plugin.className);
            intent.putExtra(PluginInfo.Field.CLASS.toString(), plugin.className);
            Log.d(toString(), ">CONFIGURE: " + intent);
            getContext().startService(intent);
        }
    }

    /**
     * Send data item list to all enabled plugins which support DATALIST requests
     *
     * @param csvData CSV encoded data list
     *                mnemonic;description;value;units
     *                ...
     *                mnemonic;description;value;units
     */
    public void sendDataList(String csvData)
    {
        Intent intent = new Intent(Plugin.DATALIST);
        intent.addCategory(Plugin.REQUEST);
        // attach data to intent
        intent.putExtra(Plugin.EXTRA_DATA, csvData);
        // loop through all identified plugins
        for (int i = 0; i < getCount(); i++)
        {
            PluginInfo plugin = getItem(i);
            // If plugin is enabled and feature DATALIST is supported
            if (plugin.enabled
                    && (plugin.features & PluginInfo.FEATURE_DATA) != 0)
            {
                intent.setClassName(plugin.packageName, plugin.className);
                Log.v(toString(), ">DATALIST: " + intent);
                getContext().startService(intent);
            }
        }
    }

    /**
     * Send data update to all enabled plugins which support DATA requests
     *
     * @param key   Key of data change
     * @param value New value of data change
     */
    public void sendDataUpdate(String key, String value)
    {
        Intent intent = new Intent(Plugin.DATA);
        intent.addCategory(Plugin.REQUEST);
        // attach data to intent
        intent.putExtra(Plugin.EXTRA_DATA, String.format("%s=%s", key, value));

        // loop through all identified plugins
        for (int i = 0; i < getCount(); i++)
        {
            PluginInfo plugin = getItem(i);
            // If plugin is enabled and feature DATA is supported
            if (plugin.enabled
                    && (plugin.features & PluginInfo.FEATURE_DATA) != 0)
            {
                intent.setClassName(plugin.packageName, plugin.className);
                Log.v(toString(), ">DATA: " + intent);
                getContext().startService(intent);
            }
        }
    }

    /**
     * Close all identified plugins
     */
    void closeAllPlugins()
    {
        // loop through all identified plugins
        for (int i = 0; i < getCount(); i++)
        {
            stopPlugin(i);
        }
        // clear list of plugins
        clear();
    }

}
