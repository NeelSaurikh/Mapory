package com.example.mapory.adapters


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mapory.activities.AddHappyPlaceActivity
import com.example.mapory.activities.MainActivity
import com.example.mapory.database.DatabaseHandler
import com.example.mapory.databinding.ItemHappyPlaceBinding
import com.example.mapory.models.HappyPlaceModel

class HappyPlacesAdapter(
    private val context: Context,
    private var list: ArrayList<HappyPlaceModel>
) : RecyclerView.Adapter<HappyPlacesAdapter.MyViewHolder>() {

    private var onClickListener: OnClickListener? = null

    inner class MyViewHolder(val binding: ItemHappyPlaceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = ItemHappyPlaceBinding.inflate(LayoutInflater.from(context), parent, false)
        return MyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val model = list[position]

        holder.binding.ivPlaceImage.setImageURI(Uri.parse(model.image))
        holder.binding.tvTitle.text = model.title
        holder.binding.tvDescription.text = model.description

        holder.binding.root.setOnClickListener {
            onClickListener?.onClick(position, model)
        }
    }

    override fun getItemCount(): Int = list.size

    fun setOnClickListener(onClickListener: OnClickListener) {
        this.onClickListener = onClickListener
    }

    fun removeAt(position: Int) {
        val dbHandler = DatabaseHandler(context)
        val isDeleted = dbHandler.deleteHappyPlace(list[position])
        if (isDeleted > 0) {
            list.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun notifyEditItem(activity: Activity, position: Int, requestCode: Int) {
        val intent = Intent(context, AddHappyPlaceActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_PLACE_DETAILS, list[position])
        activity.startActivityForResult(intent, requestCode)
        notifyItemChanged(position)
    }

    interface OnClickListener {
        fun onClick(position: Int, model: HappyPlaceModel)
    }
}