package com.fr3ts0n.androbd.plugin;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;


/**
 * Abstract AndrOBD plugin
 */
public abstract class Plugin
    extends Service
{
    /** ACTIONS */
    public static final String IDENTIFY    = "com.fr3ts0n.androbd.plugin.IDENTIFY";
    public static final String CONFIGURE   = "com.fr3ts0n.androbd.plugin.CONFIGURE";
    public static final String ACTION      = "com.fr3ts0n.androbd.plugin.ACTION";
    public static final String DATALIST    = "com.fr3ts0n.androbd.plugin.DATALIST";
    public static final String DATA        = "com.fr3ts0n.androbd.plugin.DATA";

    /** CATEGORIES */
    public static final String REQUEST     = "com.fr3ts0n.androbd.plugin.REQUEST";
    public static final String RESPONSE    = "com.fr3ts0n.androbd.plugin.RESPONSE";

    /** Parameters for DATALIST / DATA (content will be csv encoded) */
    public static final String EXTRA_DATA  = "com.fr3ts0n.androbd.plugin.extra.DATA";

    /** The system power manager */
    PowerManager mgr;
    /** Wake lock to keep service running with screen off */
    PowerManager.WakeLock wakeLock;

    /** Host application info */
    protected PluginInfo hostInfo;

    /** remember if header was sent already */
    protected boolean headerSent = false;

    /** CSV fields for data list messages */
    public enum CsvField
    {
        MNEMONIC,       /**< Mnemonic of data item */
        DESCRIPTION,    /**< Description of data item */
        MIN,            /**< minimum value */
        MAX,            /**< maximum value */
        UNITS,          /**< measurement units */
    }

    /**
     * Plugin supports configuration requests
     */
    public interface ConfigurationHandler
    {
        /**
         * Handle configuration request.
         * Perform plugin configuration
         */
        void performConfigure();
    }

    /**
     * Plugin supports Action requests
     */
    public interface ActionHandler
    {
        /**
         * Perform intended action of the plugin
         */
        void performAction();
    }

    /**
     * Plugin supports datalist / data requests
     */
    public interface DataReceiver
    {
        /**
         * Handle data list update.
         *
         * @param csvString
         * CSV data string in format key;description;value;units.
         * One line per data item
         */
        void onDataListUpdate(String csvString);

        /**
         * Handle data update.
         * @param key Key of data change
         * @param value New value of data change
         */
        void onDataUpdate(String key, String value);
    }

    /**
     * Plugin supports data provision interface
     */
    public interface DataProvider
    {
        /**
         * Send data item list to all enabled plugins which support DATALIST requests
         *
         * @param csvData CSV encoded data list
         *                mnemonic;description;value;units
         *                ...
         *                mnemonic;description;value;units
         */
        void sendDataList(String csvData);

        /**
         * Send data update to all enabled plugins which support DATA requests
         *
         * @param key Key of data change
         * @param value New value of data change
         */
        void sendDataUpdate(String key, String value);
    }

	@Override
	public void onCreate()
	{
		super.onCreate();
		/* Aquire wake lock to keep service running even with screen off */
		mgr = (PowerManager)getSystemService(Context.POWER_SERVICE);
		wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
		                           getClass().getSimpleName().concat(":WakeLock"));
		wakeLock.acquire();
	}

	@Override
    public void onDestroy()
    {
        /* Release wake lock since service shall be stopped ... */
        wakeLock.release();

        super.onDestroy();
    }

    /**
     * Start plugin service
     *
     * @param intent Intent to start sevice on
     * @param flags Additional flags for service
     * @param startId Start ID for service
     *
     * @return Service continuation flags
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        int result = super.onStartCommand(intent, flags, startId);

        if (intent != null)
        {
            final String action = intent.getAction();
            if(IDENTIFY.equals(action))
            {
                Log.v(toString(), "<IDENTIFY: " +  intent);
                handleIdentify(getApplicationContext(),intent);
            }

            if (CONFIGURE.equals(action)
                && this instanceof ConfigurationHandler)
            {
                Log.v(toString(), "<CONFIGURE: " +  intent);
                ((ConfigurationHandler)this).performConfigure( );
            }

            if (ACTION.equals(action)
                && this instanceof ActionHandler)
            {
                Log.v(toString(), "<ACTION: " + intent);
                ((ActionHandler)this).performAction( );
            }

            if(DATALIST.equals(action)
               && this instanceof DataReceiver)
            {
                Log.v(toString(), "<DATALIST: " + intent);
                String dataStr = intent.getStringExtra(EXTRA_DATA);
                ((DataReceiver)this).onDataListUpdate( dataStr );
            }

            if(DATA.equals(action)
               && this instanceof DataReceiver)
            {
                Log.v(toString(), "<DATA: " + intent);
                String dataStr = intent.getStringExtra(EXTRA_DATA);
                if(dataStr != null)
                {
                    Log.v(toString(), dataStr);
                    String[] params = dataStr.split("=");
                    ((DataReceiver)this).onDataUpdate( params[0], params[1] );
                }
                else
                {
                    Log.w(toString(), "DATA empty");
                }
            }
        }

        // ensure plugin lifecycle until stopService() call
        return result;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    /**
     * Get a translated string from resources by it's name
     * (rather than by it's resource ID)
     *
     * @param context Context of service / application to use resources from
     * @param strName Name of string resource to get
     *
     * @return Translated resource string
     */
    protected static String getStringResourceByName(Context context, String strName)
    {
        return context.getString(context.getResources()
                                        .getIdentifier(strName,
                                                       "string",
                                                       context.getPackageName()));
    }

    /**
     * Handle IDENTIFY intent
     *
     * @param context Context of intent handler
     * @param intent Intent object of identify request
     */
    protected void handleIdentify(Context context, Intent intent)
    {
        // the host application may have restarted since we last sent data headers
        headerSent = false;

        // remember broadcasting host application
        hostInfo = new PluginInfo(intent.getExtras());

        // create identify response to broadcast origin
        Intent identifyIntent = new Intent(IDENTIFY);
        identifyIntent.addCategory(RESPONSE);
        identifyIntent.putExtras(getPluginInfo().toBundle());
        Log.v(toString(), ">IDENTIFY: " + identifyIntent);
        sendBroadcast(identifyIntent);
    }

    public void sendDataList(String csvData)
    {
        // If plugin is enabled and feature DATA is supported
        if (!headerSent)
        {
            Intent intent = new Intent(Plugin.DATALIST);
            intent.addCategory(Plugin.RESPONSE);

            // attach data to intent
            intent.putExtra(Plugin.EXTRA_DATA, csvData);
            Log.d(toString(), ">DATALIST: " + intent);
            sendBroadcast(intent);
            // remember that header is sent
            headerSent = true;
        }
    }

    public void sendDataUpdate(String key, String value)
    {
        // If feature DATA is supported
        Intent intent = new Intent(Plugin.DATA);
        intent.addCategory(Plugin.RESPONSE);

        // attach data to intent
        intent.putExtra(Plugin.EXTRA_DATA, String.format("%s=%s", key, value));
        Log.d(toString(), ">DATA: " + intent);
        sendBroadcast(intent);
    }

    /**
     * get own plugin info
     */
    abstract public PluginInfo getPluginInfo();
}
