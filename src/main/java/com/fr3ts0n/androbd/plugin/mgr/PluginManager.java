package com.fr3ts0n.androbd.plugin.mgr;

import android.app.ListActivity;
import android.os.Bundle;

import com.fr3ts0n.androbd.plugin.Plugin;
import com.fr3ts0n.androbd.plugin.R;

/**
 * Plugin manager
 *
 * This class visually handles a list of detected plugins:
 * - Show list if identified plugins
 * - Allow Enabling/Disabling plugin usage
 * - Allow trigger configuration of individual plugin
 * - Allow manual triggering plugin action
 */
public abstract class PluginManager
        extends ListActivity
        implements Plugin.DataReceiver
{
    public static PluginHandler pluginHandler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if(pluginHandler == null)
        {
            pluginHandler = new PluginHandler(this);
            pluginHandler.setDataReceiver(this);
        }
    }

    @Override
    protected void onDestroy()
    {
        pluginHandler.cleanup();
        pluginHandler = null;
        super.onDestroy();
    }

    /**
     * Set Plugin manager view
     */
    protected void setManagerView()
    {
        setContentView(R.layout.content_main);
        setListAdapter(pluginHandler);
    }
}
