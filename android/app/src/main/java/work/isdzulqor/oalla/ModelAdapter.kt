package work.isdzulqor.oalla

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ModelAdapter(
    private var models: MutableList<ModelInfo>,
    private val onDeleteModel: (String) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.model_name)
        val meta: TextView = view.findViewById(R.id.model_meta)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = models.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        holder.name.text = model.name
        holder.meta.text = "Size: ${formatSize(model.sizeBytes)} | Params: ${model.parameterSize} | Updated: ${formatTimestamp(model.modifiedAt)}"
        holder.deleteButton.setOnClickListener {
            onDeleteModel(model.name)
        }
    }

    fun updateData(newList: List<ModelInfo>) {
        models = newList.toMutableList()
        notifyDataSetChanged()
    }

    private fun formatSize(size: Long): String {
        val mb = size / 1024 / 1024
        return if (mb > 1024) "%.2f GB".format(mb / 1024.0) else "%.2f MB".format(mb.toDouble())
    }

    private fun formatTimestamp(iso: String): String {
        return try {
            val cleanedIso = iso.substringBefore('+').substringBefore('.')
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val date = parser.parse(cleanedIso)
            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            formatter.format(date ?: return iso)
        } catch (e: Exception) {
            iso
        }
    }
}