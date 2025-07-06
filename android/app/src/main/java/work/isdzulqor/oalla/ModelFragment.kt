package work.isdzulqor.oalla

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONObject
import android.provider.Settings
import androidx.core.net.toUri
import kotlin.random.Random

class ModelFragment : Fragment() {
    private lateinit var modelInput: AutoCompleteTextView
    private lateinit var downloadButton: ImageButton
    private lateinit var downloadLog: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var modelList: RecyclerView
    private lateinit var adapter: ModelAdapter
    private lateinit var storageToggle: RadioGroup

    private var selectedExternalUri: Uri? = null
    private var lastLogTime = 0L
    private var lastCheckedStorageId: Int = R.id.storage_internal

    private val topModelSuggestions = listOf(
        "llama3.1:latest", "phi4-mini:latest", "gemma:2b", "qwen3:0.6b", "smollm2:135m"
    )

    private val modelSizes = mapOf(
        "llama3.1:latest" to 420,
        "phi4-mini:latest" to 130,
        "gemma:2b" to 280,
        "qwen3:0.6b" to 175,
        "smollm2:135m" to 65
    )

    private val openExternalStoragePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedExternalUri = uri

            requireContext().getSharedPreferences("model_prefs", 0).edit()
                .putString("external_uri", uri.toString())
                .apply()

            Toast.makeText(requireContext(), "External directory selected", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "No folder selected", Toast.LENGTH_SHORT).show()
            storageToggle.check(R.id.storage_internal)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_model, container, false)

        modelInput = view.findViewById(R.id.model_input)
        downloadButton = view.findViewById(R.id.download_button)
        downloadLog = view.findViewById(R.id.download_log)
        progressBar = view.findViewById(R.id.download_progress_bar)
        modelList = view.findViewById(R.id.model_list)
        storageToggle = view.findViewById(R.id.storage_toggle)

        restoreExternalUri()
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

        return view
    }

    private fun showStorageSwitchConfirmation(checkedId: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Change Storage Location")
            .setMessage("Changing the storage location won't move your existing downloaded models. They will remain in the currently selected storage.")
            .setPositiveButton("Continue") { _, _ ->
                lastCheckedStorageId = checkedId
                if (checkedId == R.id.storage_external) {
                    openExternalStoragePicker.launch(null)
                } else {
                    selectedExternalUri = null
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                storageToggle.setOnCheckedChangeListener(null)
                storageToggle.check(lastCheckedStorageId)
                storageToggle.setOnCheckedChangeListener { _, id ->
                    if (id != lastCheckedStorageId) {
                        showStorageSwitchConfirmation(id)
                    }
                }
            }
            .show()
    }

    private fun restoreExternalUri() {
        val savedUri = requireContext().getSharedPreferences("model_prefs", 0)
            .getString("external_uri", null)

        savedUri?.let {
            try {
                val uri = Uri.parse(it)
                val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
                if (docFile != null && docFile.canWrite()) {
                    selectedExternalUri = uri
                    storageToggle.check(R.id.storage_external)
                    lastCheckedStorageId = R.id.storage_external
                }
            } catch (e: Exception) {
                Log.w("StorageRestore", "Invalid saved external URI")
            }
        }
    }

    private fun setupAutocomplete() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, topModelSuggestions)
        modelInput.setAdapter(adapter)
        modelInput.threshold = 1
    }

    private fun setupRecyclerView() {
        adapter = ModelAdapter(mutableListOf()) { modelName -> confirmDeleteModel(modelName) }
        modelList.layoutManager = LinearLayoutManager(requireContext())
        modelList.adapter = adapter
    }

    private fun confirmDeleteModel(name: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Model")
            .setMessage("Are you sure you want to delete \"$name\"?")
            .setPositiveButton("Delete") { _, _ -> deleteModel(name) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteModel(name: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val requestBody = """{"name":"$name"}""".toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://localhost:9090/api/delete")
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
                val request = Request.Builder()
                    .url("http://localhost:9090/api/tags")
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
        val serviceIntent = Intent(requireContext(), DownloadService::class.java)
        requireContext().startService(serviceIntent)

        downloadLog.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        progressBar.isIndeterminate = true
        downloadLog.text = "Starting model pull..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val requestBody = """{"name":"$modelName"}""".toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("http://localhost:9090/api/pull")
                    .post(requestBody)
                    .build()
                val response = client.newCall(request).execute()
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
                                parseAndUpdateProgress(line)
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    downloadLog.text = "Download complete"
                    fetchModelList()
                    DownloadService.instance?.finishDownload()

                    Handler(Looper.getMainLooper()).postDelayed({
                        downloadLog.visibility = View.GONE
                        progressBar.visibility = View.GONE
                    }, 1500)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadLog.text = "Error: ${e.localizedMessage}"
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun parseAndUpdateProgress(line: String) {
        try {
            val json = JSONObject(line)
            if (!json.has("completed") || !json.has("total")) return
            val completed = json.getLong("completed")
            val total = json.getLong("total")
            if (total <= 0) return

            val percent = (completed.toDouble() / total.toDouble() * 100).toInt()
            val mbDone = completed / 1024 / 1024
            val mbTotal = total / 1024 / 1024
            val logText = "Progress: $mbDone / $mbTotal MB ($percent%)"

            val now = SystemClock.elapsedRealtime()
            if (now - lastLogTime > 2000 || completed == total) {
                downloadLog.text = logText
                progressBar.progress = percent
                progressBar.isIndeterminate = false
                lastLogTime = now
            }

            DownloadService.instance?.updateProgress(percent, logText)
        } catch (e: Exception) {
            // Ignore malformed lines
        }
    }

    private fun showConfirmDownloadDialog(modelName: String) {
        val size = modelSizes[modelName] ?: Random.nextInt(100, 400)

        val message = """
        You are about to download:
        
        • Model: $modelName
        • Estimated size: ~$size MB
        
        To see download progress, please ensure notifications are enabled.
        Also, avoid closing or force-stopping the app during the download, as it may cancel the process.
    """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Download Confirmation")
            .setMessage(message)
            .setPositiveButton("Download") { _, _ ->
                if (!isNotificationEnabled(requireContext())) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Enable Notifications")
                        .setMessage("To track download progress, please enable notifications for this app.")
                        .setPositiveButton("Open Settings") { _, _ -> openNotificationSettings() }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    downloadModel(modelName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
}