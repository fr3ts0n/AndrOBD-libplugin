package com.fr3ts0n.androbd.plugin.mgr;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

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
public class PluginManager
        extends AppCompatActivity
        implements Plugin.DataReceiver
{
    public static PluginHandler pluginHandler = null;

    /**
     * ListActivity auto-managed this via the android:id/list convention;
     * AppCompatActivity requires the same lookup done explicitly.
     */
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // create a new PluginHandler
        if(pluginHandler == null)
        {
            pluginHandler = new PluginHandler(this);
            pluginHandler.setDataReceiver(this);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // discover plugins after showing the activity
        // before onResume completes, the app is still in the background and can't start services
        new Handler().post(new Runnable()
        {
            @Override
            public void run()
            {
                pluginHandler.setup();
            }
        });
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
        listView = findViewById(android.R.id.list);
        listView.setAdapter(pluginHandler);
    }

    /**
     * Re-resolve the android:id/list lookup whenever a subclass (e.g.
     * MainActivity) replaces the content view directly, rather than going
     * through setManagerView(). ListActivity did this generically for any
     * content view; AppCompatActivity has no equivalent, and the previous
     * findViewById() call in setManagerView() alone left listView null for
     * any other setContentView(View) call, since it's only reached from
     * that one method.
     */
    @Override
    public void setContentView(View view)
    {
        super.setContentView(view);
        ListView newListView = findViewById(android.R.id.list);
        if (newListView != null)
        {
            listView = newListView;
        }
    }

    /**
     * Compatibility accessors matching ListActivity's own API, so subclasses
     * (MainActivity) that relied on the inherited getListView()/getListAdapter()/
     * setListAdapter() need no changes themselves.
     */
    public ListView getListView()
    {
        return listView;
    }

    public ListAdapter getListAdapter()
    {
        return listView.getAdapter();
    }

    public void setListAdapter(ListAdapter adapter)
    {
        listView.setAdapter(adapter);
    }

    /**
     * Dialog / view handler of IDENTIFY button
     *
     * @param view view which triggers the event
     */
    public void sendIdentify(View view)
    {
        pluginHandler.clear();
        pluginHandler.identifyPlugins();
    }

    /**
     * Dialog / view handler of CONFIG button
     *
     * @param view view which triggers the event
     */
    public void sendConfigure(View view)
    {
        int pos = listView.getPositionForView(view);
        pluginHandler.triggerConfiguration(pos);
    }

    /**
     * Dialog / view handler of ACTION button
     *
     * @param view view which triggers the event
     */
    public void sendPerformAction(View view)
    {
        int pos = listView.getPositionForView(view);
        pluginHandler.triggerAction(pos);
    }

    /**
     * Dialog / view handler of Enable/Disable switch
     *
     * @param view view which triggers the event
     */
    public void setPluginEnabled(View view)
    {
        int pos = listView.getPositionForView(view);
        pluginHandler.setPluginEnabled(pos, ((Switch) view).isChecked());
    }

    @Override
    public void onDataListUpdate(String csvString)
    {
        // Dummy implementation to be overridden
    }

    @Override
    public void onDataUpdate(String key, String value)
    {
        // Dummy implementation to be overridden
    }
}
