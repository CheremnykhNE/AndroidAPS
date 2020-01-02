package info.nightscout.androidaps.plugins.source

import android.content.Intent
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.BgSourceInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSgv
import info.nightscout.androidaps.utils.JsonHelper.safeGetLong
import info.nightscout.androidaps.utils.JsonHelper.safeGetString
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientSourcePlugin @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP
) : PluginBase(PluginDescription()
    .mainType(PluginType.BGSOURCE)
    .fragmentClass(BGSourceFragment::class.java.name)
    .pluginName(R.string.nsclientbg)
    .description(R.string.description_source_ns_client)
), BgSourceInterface {

    private var lastBGTimeStamp: Long = 0
    private var isAdvancedFilteringEnabled = false

    override fun advancedFilteringSupported(): Boolean {
        return isAdvancedFilteringEnabled
    }

    override fun handleNewData(intent: Intent) {
        if (!isEnabled(PluginType.BGSOURCE) && !sp.getBoolean(R.string.key_ns_autobackfill, true)) return
        val bundles = intent.extras ?: return
        try {
            if (bundles.containsKey("sgv")) {
                val sgvstring = bundles.getString("sgv")
                aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $sgvstring")
                val sgvJson = JSONObject(sgvstring)
                storeSgv(sgvJson)
            }
            if (bundles.containsKey("sgvs")) {
                val sgvstring = bundles.getString("sgvs")
                aapsLogger.debug(LTag.BGSOURCE, "Received NS Data: $sgvstring")
                val jsonArray = JSONArray(sgvstring)
                for (i in 0 until jsonArray.length()) {
                    val sgvJson = jsonArray.getJSONObject(i)
                    storeSgv(sgvJson)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
        // Objectives 0
        sp.putBoolean(R.string.key_ObjectivesbgIsAvailableInNS, true)
    }

    private fun storeSgv(sgvJson: JSONObject) {
        val nsSgv = NSSgv(sgvJson)
        val bgReading = BgReading(nsSgv)
        MainApp.getDbHelper().createIfNotExists(bgReading, "NS")
        detectSource(safeGetString(sgvJson, "device", "none"), safeGetLong(sgvJson, "mills"))
    }

    fun detectSource(source: String, timeStamp: Long) {
        if (timeStamp > lastBGTimeStamp) {
            isAdvancedFilteringEnabled = source.contains("G5 Native") || source.contains("G6 Native") || source.contains("AndroidAPS-DexcomG5") || source.contains("AndroidAPS-DexcomG6")
            lastBGTimeStamp = timeStamp
        }
    }
}