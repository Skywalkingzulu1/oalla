package work.isdzulqor.oalla

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import android.provider.Settings
import androidx.core.net.toUri
import com.google.gson.Gson

class ModelFragment : Fragment() {
    private lateinit var modelInput: AutoCompleteTextView
    private lateinit var downloadButton: ImageButton
    private lateinit var downloadLog: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var modelList: RecyclerView
    private lateinit var adapter: ModelAdapter
    private lateinit var storageToggle: RadioGroup
    private lateinit var modelSuggestionAdapter: ModelSuggestionAdapter
    private lateinit var downloadContainer: FrameLayout

    private var currentCall: okhttp3.Call? = null
    private var currentModelName: String? = null
    private lateinit var cancelButton: ImageButton
    private var lastProgressText: String = ""

    private var selectedStorageType: String = "internal"
    private var lastLogTime = 0L
    private var lastCheckedStorageId: Int = R.id.storage_internal
    private var modelDataList: List<ModelData> = emptyList()
    private var allSuggestions: List<ModelSuggestion> = emptyList()

    private val serverPort: Int
        get() = (activity as? MainActivity)?.SERVER_PORT
            ?: throw IllegalStateException("SERVER_PORT is not set in MainActivity")

    private val userAgentSecret: String
        get() = (activity as? MainActivity)?.USER_AGENT_SECRET
            ?: throw IllegalStateException("USER_AGENT_SECRET is not set in MainActivity")

    private val apiBaseUrl: String
        get() = "http://localhost:$serverPort"

    private fun buildRequestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("User-Agent", userAgentSecret)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_model, container, false)

        modelInput = view.findViewById(R.id.model_input)
        downloadButton = view.findViewById(R.id.download_button)
        downloadLog = view.findViewById(R.id.download_log)
        progressBar = view.findViewById(R.id.download_progress_bar)
        modelList = view.findViewById(R.id.model_list)
        storageToggle = view.findViewById(R.id.storage_toggle)
        downloadContainer = view.findViewById(R.id.download_container)
        cancelButton = view.findViewById(R.id.cancel_button)
        cancelButton.setOnClickListener {
            cancelDownload()
        }

        restoreStoragePreference()
        loadModelDataFromAssets()
        setupAutocomplete()
        setupRecyclerView()
        fetchModelList()

        downloadButton.setOnClickListener {
            val modelName = modelInput.text.toString().trim()
            if (modelName.isEmpty()) {
                Toast.makeText(requireContext(), "Enter model name", Toast.LENGTH_SHORT).show()
            } else {
                showConfirmDownloadDialog(modelName)
            }
        }

        storageToggle.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != lastCheckedStorageId) {
                showStorageSwitchConfirmation(checkedId)
            }
        }

        // Handle selection from dropdown
        modelInput.setOnItemClickListener { _, _, position, _ ->
            val selected = modelSuggestionAdapter.getItem(position)
            modelInput.setText(selected.modelName)
            modelInput.setSelection(selected.modelName.length)
        }

        return view
    }

    private fun cancelDownload() {
        val name = currentModelName ?: return
        currentCall?.cancel()
        currentCall = null
        currentModelName = null

        // Stop foreground service and clear notification
        val manager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(DownloadService.NOTIFICATION_ID)

        if (DownloadService.instance != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                DownloadService.instance?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                DownloadService.instance?.stopForeground(true)
            }
            DownloadService.instance?.stopSelf()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val requestBody = """{"name":"$name"}""".toRequestBody("application/json".toMediaType())
                val request = buildRequestBuilder("$apiBaseUrl/api/delete")
                    .delete(requestBody)
                    .build()
                client.newCall(request).execute()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Download cancelled", Toast.LENGTH_SHORT).show()

                    cancelButton.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    downloadLog.text = "Download cancelled"

                    Handler(Looper.getMainLooper()).postDelayed({
                        downloadContainer.visibility = View.GONE
                    }, 2000)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Cancel failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadModelDataFromAssets() {
        try {
            val assetManager = requireContext().assets
            val jsonString = assetManager.open("models.json").bufferedReader().use { it.readText() }

            val jsonArray = JSONArray(jsonString)
            val gson = Gson()

            val parsedList = mutableListOf<ModelData>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i).toString()
                val modelData = gson.fromJson(item, ModelData::class.java)
                parsedList.add(modelData)
            }

            modelDataList = parsedList

            // Create flat list of all suggestions
            allSuggestions = modelDataList.flatMap { model ->
                model.data.variants.map { variant ->
                    ModelSuggestion(
                        displayText = "${variant.model} (${variant.size}, ${variant.context})",
                        modelName = variant.model,
                        size = variant.size,
                        context = variant.context
                    )
                }
            }

            Log.e("ModelFragment", "Parsed ${modelDataList.size} models")

        } catch (e: Exception) {
            Log.e("ModelFragment", "Failed to load model data: ${e.message}")
            allSuggestions = emptyList()
        }
    }

    private fun showStorageSwitchConfirmation(checkedId: Int) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom, null)
        view.findViewById<TextView>(R.id.customTitle).text = "Change Storage Location"
        view.findViewById<TextView>(R.id.customMessage).text =
            "Changing the storage location won't move your existing downloaded models. They will remain in the currently selected storage."

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(view)
            .setPositiveButton("Continue") { _, _ ->
                lastCheckedStorageId = checkedId

                val newStorage = if (checkedId == R.id.storage_external) "external" else "internal"
                selectedStorageType = newStorage

                requireContext().getSharedPreferences("model_prefs", 0)
                    .edit()
                    .putString("storage_model", newStorage)
                    .commit()

                Toast.makeText(requireContext(), "Storage set to $newStorage", Toast.LENGTH_SHORT).show()
                restartApp()
            }
            .setNegativeButton("Cancel") { _, _ ->
                revertStorageToggle()
            }
            .setOnCancelListener {
                revertStorageToggle()
            }
            .create()

        dialog.show()
    }

    private fun revertStorageToggle() {
        storageToggle.setOnCheckedChangeListener(null)
        storageToggle.check(lastCheckedStorageId)
        storageToggle.setOnCheckedChangeListener { _, id ->
            if (id != lastCheckedStorageId) {
                showStorageSwitchConfirmation(id)
            }
        }
    }

    private fun restoreStoragePreference() {
        selectedStorageType = requireContext()
            .getSharedPreferences("model_prefs", 0)
            .getString("storage_model", "internal") ?: "internal"

        if (selectedStorageType == "external") {
            storageToggle.check(R.id.storage_external)
            lastCheckedStorageId = R.id.storage_external
        } else {
            storageToggle.check(R.id.storage_internal)
            lastCheckedStorageId = R.id.storage_internal
        }
    }

    private fun setupAutocomplete() {
        modelSuggestionAdapter = ModelSuggestionAdapter(requireContext(), allSuggestions)
        modelInput.setAdapter(modelSuggestionAdapter)
        modelInput.threshold = 0 // Show suggestions immediately

        // Show dropdown when focused
        modelInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && modelInput.text.isEmpty()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isAdded && modelInput.hasFocus()) {
                        modelInput.showDropDown()
                    }
                }, 150) // small delay ensures stability
            }
        }

        // Show dropdown on click if empty
        modelInput.setOnClickListener {
            if (modelInput.text.isEmpty()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isAdded && modelInput.hasFocus()) {
                        modelInput.showDropDown()
                    }
                }, 150)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ModelAdapter(mutableListOf()) { modelName -> confirmDeleteModel(modelName) }
        modelList.layoutManager = LinearLayoutManager(requireContext())
        modelList.adapter = adapter
    }

    private fun confirmDeleteModel(name: String) {
        showCustomDialog(
            title = "Delete Model",
            message = "Are you sure you want to delete \"$name\"?",
            positiveText = "Delete",
            negativeText = "Cancel",
            onPositive = { deleteModel(name) },
            onNegative = {}
        )
    }

    private fun deleteModel(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val requestBody = """{"name":"$name"}""".toRequestBody("application/json".toMediaType())
                val request = buildRequestBuilder("$apiBaseUrl/api/delete")
                    .delete(requestBody)
                    .build()
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        fetchModelList()
                        Toast.makeText(requireContext(), "Model deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete model", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchModelList() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = buildRequestBuilder("$apiBaseUrl/api/tags")
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                val json = response.body?.string() ?: return@launch
                val modelList = parseModelListFromJson(json)
                withContext(Dispatchers.Main) {
                    adapter.updateData(modelList)
                }
            } catch (e: Exception) {
                Log.e("ModelFetch", "Failed to fetch model list: ${e.message}")
            }
        }
    }

    private fun parseModelListFromJson(json: String): List<ModelInfo> {
        val result = mutableListOf<ModelInfo>()
        val root = JSONObject(json)
        val models = root.getJSONArray("models")
        for (i in 0 until models.length()) {
            val obj = models.getJSONObject(i)
            val name = obj.getString("name")
            val modifiedAt = obj.getString("modified_at")
            val size = obj.getLong("size")
            val paramSize = obj.getJSONObject("details").getString("parameter_size")
            result.add(ModelInfo(name, paramSize, size, modifiedAt))
        }
        return result
    }

    private fun downloadModel(modelName: String) {
        currentModelName = modelName
        cancelButton.visibility = View.VISIBLE
        downloadContainer.visibility = View.VISIBLE
        downloadLog.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        downloadLog.text = "Starting model pull..."

        modelInput.isEnabled = false // ❗ Disable input

        CoroutineScope(Dispatchers.IO).launch {
            var hasError = false

            try {
                val client = OkHttpClient()
                val requestBody = """{"name":"$modelName"}""".toRequestBody("application/json".toMediaType())
                val request = buildRequestBuilder("$apiBaseUrl/api/pull")
                    .post(requestBody)
                    .build()

                currentCall = client.newCall(request)
                val response = currentCall!!.execute()

                val source = response.body?.source() ?: return@launch
                val buffer = Buffer()
                var partial = ""

                while (!source.exhausted()) {
                    source.read(buffer, 8192)
                    val chunk = buffer.readUtf8()
                    partial += chunk
                    val lines = partial.split("\n")
                    partial = lines.last()

                    for (line in lines.dropLast(1)) {
                        if (line.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                (requireActivity() as? MainActivity)?.keepScreenOnFor(5 * 1000L) // keep screen on for 5 seconds
                                val isError = parseAndUpdateProgress(line)
                                if (isError) hasError = true
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (!hasError) {
                        downloadLog.text = "Download complete"
                    }

                    cancelButton.visibility = View.GONE
                    fetchModelList()
                    DownloadService.instance?.finishDownload()

                    // ✅ Re-enable and clear input
                    modelInput.isEnabled = true
                    modelInput.setText("")

                    Handler(Looper.getMainLooper()).postDelayed({
                        downloadContainer.visibility = View.GONE
                        downloadLog.visibility = View.GONE
                        progressBar.visibility = View.GONE
                    }, 2000)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadLog.text = "Error: ${e.localizedMessage}"
                    cancelButton.visibility = View.GONE
                    progressBar.visibility = View.GONE

                    // ✅ Re-enable input if error
                    modelInput.isEnabled = true
                }
            } finally {
                currentCall = null
                currentModelName = null
            }
        }
    }

    private fun parseAndUpdateProgress(line: String): Boolean {
        try {
            val json = JSONObject(line)

            if (json.has("error")) {
                val errorMsg = json.getString("error")
                lastProgressText = "Error: $errorMsg"
                downloadLog.text = lastProgressText
                progressBar.isIndeterminate = false
                progressBar.progress = 0
                DownloadService.instance?.updateProgress(0, lastProgressText)
                return true // error detected
            }

            if (!json.has("completed") || !json.has("total")) return false

            val completed = json.getLong("completed")
            val total = json.getLong("total")
            if (total <= 0) return false

            val percent = (completed.toDouble() / total.toDouble() * 100).toInt()
            val mbDone = completed / 1024 / 1024
            val mbTotal = total / 1024 / 1024

            if (mbTotal == 0L && mbDone == 0L) return false

            lastProgressText = "Progress: $mbDone / $mbTotal MB ($percent%)"

            val now = SystemClock.elapsedRealtime()
            if (now - lastLogTime > 2000 || completed == total) {
                downloadLog.text = lastProgressText
                progressBar.progress = percent
                progressBar.isIndeterminate = false
                lastLogTime = now
            }

            DownloadService.instance?.updateProgress(percent, lastProgressText)
        } catch (_: Exception) {
            // ignore
        }

        return false
    }

    private fun showConfirmDownloadDialog(modelName: String) {
        // Try to find size from our model data
        val modelInfo = allSuggestions.find { it.modelName == modelName }
        val sizeDisplay = modelInfo?.size ?: "Unknown size"

        val message = """
        You are about to download:
        
        • Model: $modelName
        • Size: $sizeDisplay
        ${modelInfo?.let { "• Context: ${it.context}" } ?: ""}
        
        To see download progress, please ensure notifications are enabled.
        Also, avoid closing or force-stopping the app during the download, as it may cancel the process.
    """.trimIndent()

        showCustomDialog(
            title = "Download Confirmation",
            message = message,
            positiveText = "Download",
            negativeText = "Cancel",
            onPositive = {
                if (!isNotificationEnabled(requireContext())) {
                    showCustomDialog(
                        title = "Enable Notifications",
                        message = "To track download progress, please enable notifications for this app.",
                        positiveText = "Open Settings",
                        negativeText = "Cancel",
                        onPositive = { openNotificationSettings() },
                        onNegative = {}
                    )
                } else {
                    val intent = Intent(requireContext(), DownloadService::class.java)
                    requireContext().startService(intent) // Start foreground service!
                    downloadModel(modelName)
                }
            },
            onNegative = {}
        )
    }

    private fun isNotificationEnabled(context: Context): Boolean {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.areNotificationsEnabled()
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${requireContext().packageName}".toUri()
            }
        }

        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "Unable to open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCustomDialog(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String?,
        onPositive: () -> Unit,
        onNegative: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom, null)
        view.findViewById<TextView>(R.id.customTitle).text = title
        view.findViewById<TextView>(R.id.customMessage).text = message

        val builder = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(view)
            .setPositiveButton(positiveText) { _, _ -> onPositive() }

        if (negativeText != null && onNegative != null) {
            builder.setNegativeButton(negativeText) { _, _ -> onNegative() }
        }

        builder.show()
    }

    private fun restartApp() {
        val intent = requireActivity().baseContext.packageManager
            .getLaunchIntentForPackage(requireActivity().baseContext.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        requireActivity().finish()
        Runtime.getRuntime().exit(0)
    }
}