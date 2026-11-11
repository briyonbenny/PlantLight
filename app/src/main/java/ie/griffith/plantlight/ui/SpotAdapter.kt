package ie.griffith.plantlight.ui

import android.content.res.ColorStateList
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import ie.griffith.plantlight.R
import ie.griffith.plantlight.data.LightLevel
import ie.griffith.plantlight.data.LightUnit
import ie.griffith.plantlight.data.SettingsRepository
import ie.griffith.plantlight.data.Spot
import ie.griffith.plantlight.databinding.ItemSpotBinding
import java.io.File
import java.text.DateFormat
import java.util.Date

class SpotAdapter(
    private val unitProvider: () -> LightUnit,
    private val onOpen: (Spot) -> Unit,
    private val onDelete: (Spot) -> Unit
) : ListAdapter<Spot, SpotAdapter.Holder>(Diff) {

    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    object Diff : DiffUtil.ItemCallback<Spot>() {
        override fun areItemsTheSame(oldItem: Spot, newItem: Spot) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Spot, newItem: Spot) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSpotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemSpotBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(spot: Spot) {
            val level = LightLevel.fromStorage(spot.lightLevel)
            val context = binding.root.context
            binding.textName.text = spot.name
            binding.textLevel.text = context.getString(R.string.level_light, level.label)
            binding.textLevel.setBackgroundResource(
                when (level) {
                    LightLevel.LOW -> R.drawable.bg_badge_low
                    LightLevel.MEDIUM -> R.drawable.bg_badge_medium
                    LightLevel.BRIGHT -> R.drawable.bg_badge_bright
                }
            )
            val reading = if (spot.isManual) {
                context.getString(R.string.reading_manual_short)
            } else {
                SettingsRepository.format(spot.averageLux, unitProvider())
            }
            binding.textMeta.text = context.getString(
                R.string.spot_meta,
                reading,
                dateFormat.format(Date(spot.createdAt))
            )

            val pad = (10 * binding.root.resources.displayMetrics.density).toInt()
            if (!spot.photoPath.isNullOrBlank() && File(spot.photoPath).exists()) {
                binding.imageThumb.setContentPadding(0, 0, 0, 0)
                binding.imageThumb.imageTintList = null
                binding.imageThumb.setImageURI(Uri.fromFile(File(spot.photoPath)))
            } else {
                binding.imageThumb.setContentPadding(pad, pad, pad, pad)
                binding.imageThumb.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.forest)
                )
                binding.imageThumb.setImageResource(R.drawable.ic_leaf)
            }

            binding.root.setOnClickListener { onOpen(spot) }
            binding.buttonDelete.setOnClickListener { onDelete(spot) }
        }
    }
}
