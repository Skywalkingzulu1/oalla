package work.isdzulqor.oalla

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView

class ModelSuggestionAdapter(
    private val context: Context,
    private val allSuggestions: List<ModelSuggestion>
) : BaseAdapter(), Filterable {

    private var filteredSuggestions: List<ModelSuggestion> = getTop5DefaultSuggestions()

    private fun getTop5DefaultSuggestions(): List<ModelSuggestion> {
        val topModels = listOf("llama3.2", "deepseek-r1", "qwen3", "mistral", "tinyllama")

        return topModels.mapNotNull { modelPrefix ->
            allSuggestions
                .filter { it.modelName.startsWith("$modelPrefix:") } // stricter matching to avoid false positives
                .minByOrNull { parseSize(it.size) }
        }
    }

    private fun parseSize(sizeStr: String): Long {
        val number = sizeStr.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
        return when {
            sizeStr.contains("GB") -> (number * 1024 * 1024 * 1024).toLong()
            sizeStr.contains("MB") -> (number * 1024 * 1024).toLong()
            else -> 0L
        }
    }

    override fun getCount(): Int = filteredSuggestions.size

    override fun getItem(position: Int): ModelSuggestion = filteredSuggestions[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)

        val item = getItem(position)

        view.findViewById<TextView>(android.R.id.text1).apply {
            text = "${item.modelName} • ${item.size} • ${item.context} context"
            setTextColor(context.getColor(R.color.text_tertiary))
            setPadding(16, 12, 16, 12)
            textSize = 14f // set text size in scaled pixels (sp)
        }

        view.setBackgroundColor(context.getColor(R.color.a_bit_grey))

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase() ?: ""

                val filtered = if (query.isEmpty()) {
                    getTop5DefaultSuggestions()
                } else {
                    allSuggestions
                        .filter { it.modelName.lowercase().contains(query) }
                        .sortedWith(compareBy(
                            // Prioritize prefix matches
                            { !it.modelName.lowercase().startsWith(query) },
                            // Then by string similarity
                            { it.modelName.lowercase() }
                        ))
                        .take(10)
                }

                return FilterResults().apply {
                    values = filtered
                    count = filtered.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredSuggestions = results?.values as? List<ModelSuggestion> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}