package com.rimuru.android.ecgify.ui.home.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.rimuru.android.ecgify.databinding.ItemEcgGraphBinding
import com.rimuru.android.ecgify.digitization.model.Lead

class EcgGraphAdapter(
    private val onItemClick: (Lead) -> Unit
) : ListAdapter<EcgGraphAdapter.EcgGraphItem, EcgGraphAdapter.ViewHolder>(DiffCallback()) {

    data class EcgGraphItem(
        val lead: Lead,
        val data: List<Float>
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEcgGraphBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemEcgGraphBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EcgGraphItem) {
            binding.tvLeadName.text = item.lead.name

            // Цвет названия отведения (можно оставить разный или сделать один)
            val textColor = Color.parseColor("#00E676")  // Тот же, что и линия
            binding.tvLeadName.setTextColor(textColor)

            setupChart(item.data, item.lead.name)

            binding.root.setOnClickListener {
                onItemClick(item.lead)
            }
        }

        private fun setupChart(rawData: List<Float>, leadName: String) {
            // === КОСТЫЛЬ: делаем так, чтобы линия начиналась с 0 мВ ===
            if (rawData.isEmpty()) return
            val baseline = rawData.first()  // Первое значение — считаем изолинией
            val data = rawData.map { it - baseline }  // Вычитаем baseline

            val entries = ArrayList<Entry>()
            data.forEachIndexed { index, value ->
                entries.add(Entry(index.toFloat(), value))
            }

            val dataSet = LineDataSet(entries, leadName).apply {
                color = Color.parseColor("#00E676")  // Яркий зелёный (neon_glow)
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                setDrawFilled(false)
                mode = LineDataSet.Mode.LINEAR
                highLightColor = Color.RED
                isHighlightEnabled = true
            }

            val lineData = LineData(dataSet)

            binding.chart.apply {
                this.data = lineData
                description.isEnabled = false
                legend.isEnabled = false

                // Скролл и зум только по горизонтали
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                isScaleXEnabled = true
                isScaleYEnabled = false
                setPinchZoom(true)

                // === Ось X: время в секундах (500 Гц → 1 точка = 0.002 сек) ===
                xAxis.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#E0E0E0")
                    gridLineWidth = 0.5f
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 250f  // 0.5 секунды
                    axisMinimum = 0f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val seconds = value / 500f
                            return "%.1f с".format(seconds)
                        }
                    }
                }

                // === Ось Y: амплитуда в мВ, фиксированный диапазон ===
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#E0E0E0")
                    gridLineWidth = 0.5f
                    axisMinimum = -0.5f
                    axisMaximum = 0.5f
                    granularity = 0.5f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "%.1f мВ".format(value)
                        }
                    }
                }

                axisRight.isEnabled = false

                // Отступы для красоты
                extraBottomOffset = 10f
                extraTopOffset = 10f
                extraLeftOffset = 10f
                extraRightOffset = 10f

                invalidate()
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<EcgGraphItem>() {
        override fun areItemsTheSame(oldItem: EcgGraphItem, newItem: EcgGraphItem): Boolean {
            return oldItem.lead == newItem.lead
        }

        override fun areContentsTheSame(oldItem: EcgGraphItem, newItem: EcgGraphItem): Boolean {
            return oldItem.data == newItem.data
        }
    }
}