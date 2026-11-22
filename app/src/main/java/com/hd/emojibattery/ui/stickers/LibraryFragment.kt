package com.hd.emojibattery.ui.stickers

import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hd.emojibattery.R
import com.hd.emojibattery.runtime.Favorites
import com.hd.emojibattery.ui.common.BaseFeatureFragment
import com.hd.emojibattery.ui.common.CatalogEntry
import com.hd.emojibattery.ui.common.toCatalogEntries
import com.hd.emojibattery.ui.navigation.ScreenIds

/** Native controls over the artwork exported from Figma 870:3717 / 3821 / 4032. */
open class LibraryFragment<VM : StickerViewModel> : BaseFeatureFragment<VM>() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (editing) { editing = false; selected.clear(); render() }
                    else finishFeature()
                }
            })
    }
    private data class SavedItem(val source: String, val entry: CatalogEntry) {
        val key get() = Favorites.key(source, entry.id)
    }
    private var tab = 0
    private var editing = false
    private val selected = mutableSetOf<String>()
    private var items = emptyList<SavedItem>()
    private var panel: LinearLayout? = null
    private var deleteDialog: android.app.Dialog? = null
    private var pendingDelete = false
    private var renderedTab = -1
    private val sources = listOf(listOf("battery_styles", "themes", "animations", "charging_animations", "wallpapers"), listOf("emojis", "emotions"), listOf("stickers"), listOf("pets"))
    private fun px(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: String, radius: Int) = GradientDrawable().apply { setColor(Color.parseColor(color)); cornerRadius = px(radius).toFloat() }
    private fun label(value: String, size: Float = 16f, color: String = "#27205F") = TextView(requireContext()).apply {
        text = value; textSize = size; setTextColor(Color.parseColor(color)); gravity = Gravity.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.nunito_family)
        includeFontPadding = false
    }
    private fun icon(resource: Int, description: String) = ImageButton(requireContext()).apply {
        setImageResource(resource); contentDescription = description; background = null; scaleType = ImageView.ScaleType.FIT_CENTER
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = savedInstanceState?.getInt("library_tab") ?: if (screen == ScreenIds.FAVORITES) 0 else 2
        editing = savedInstanceState?.getBoolean("library_edit") ?: (screen != ScreenIds.FAVORITES)
        selected.addAll(savedInstanceState?.getStringArrayList("library_selected").orEmpty())
        pendingDelete = savedInstanceState?.getBoolean("library_delete_pending") ?: (screen == ScreenIds.FAVORITES_DELETE)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("library_tab", tab); outState.putBoolean("library_edit", editing)
        outState.putStringArrayList("library_selected", ArrayList(selected))
        outState.putBoolean("library_delete_pending", pendingDelete || deleteDialog?.isShowing == true)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); if (panel != null) render() }
    override fun onDestroyView() { panel = null; deleteDialog = null; renderedTab = -1; super.onDestroyView() }
    override fun bindFeature() {
        binding.featureToolbar.isVisible = false
        binding.featureScroll.isVisible = false
        if (panel == null) {
            panel = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            (binding.featureScroll.parent as ViewGroup).addView(panel, 0, ViewGroup.LayoutParams(-1, -1))
        }
        render()
    }
    private fun render() {
        val root = panel ?: return
        items = sources[tab].flatMap { source -> catalog().array(source).toCatalogEntries { Favorites.contains(prefs, source, it) }
            .filter { it.favorite }.map { SavedItem(source, it) } }
        selected.retainAll(items.map { it.key }.toSet())
        val oldGrid = root.findViewWithTag<RecyclerView>("library_grid")
        val scrollState = if (renderedTab == tab) oldGrid?.layoutManager?.onSaveInstanceState() else null
        oldGrid?.adapter = null // Recycle holders and cancel pending artwork requests.
        renderedTab = tab
        root.removeAllViews()
        val header = FrameLayout(requireContext()).apply {
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadii = floatArrayOf(0f,0f,0f,0f,px(35).toFloat(),px(35).toFloat(),px(35).toFloat(),px(35).toFloat()) }
            clipToOutline = true
        }
        root.addView(header, LinearLayout.LayoutParams(-1, px(219)))
        header.addView(ImageView(requireContext()).apply { setImageResource(R.drawable.figma_library_banner); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1,-1))
        header.addView(icon(R.drawable.figma_library_back, "Back").apply {
            setPadding(px(16), px(12), px(17), px(13)); setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }, FrameLayout.LayoutParams(px(44),px(44)).apply { leftMargin=px(7); topMargin=px(22) })
        header.addView(icon(if(editing) R.drawable.figma_library_close else R.drawable.figma_library_edit, if(editing) "Finish selection" else "Select favorites").apply {
            setPadding(px(7),px(7),px(7),px(7)); setOnClickListener { editing = !editing; selected.clear(); render() }
        }, FrameLayout.LayoutParams(px(44),px(44),Gravity.TOP or Gravity.END).apply { rightMargin=px(16); topMargin=px(22) })
        header.addView(label("Favorite",24f).apply { gravity=Gravity.START; setTypeface(typeface, android.graphics.Typeface.BOLD) }, FrameLayout.LayoutParams(-2,px(33)).apply { leftMargin=px(23);topMargin=px(84) })
        header.addView(label("All your picks in one place").apply { gravity=Gravity.START or Gravity.CENTER_VERTICAL }, FrameLayout.LayoutParams(-2,px(42)).apply { leftMargin=px(23);topMargin=px(110) })
        val tabs = LinearLayout(requireContext())
        root.addView(tabs,LinearLayout.LayoutParams(-1,px(68)))
        listOf("Battery","Emoji","Sticker","Pet").forEachIndexed { index, title ->
            val cell=LinearLayout(requireContext()).apply { orientation=LinearLayout.VERTICAL }
            tabs.addView(cell,LinearLayout.LayoutParams(0,-1,1f))
            cell.addView(label(title,14f).apply { setPadding(0,px(10),0,0); isSelected=index==tab; setOnClickListener { tab=index;selected.clear();render() } },LinearLayout.LayoutParams(-1,0,1f))
            cell.addView(View(requireContext()).apply { if(index==tab) setBackgroundResource(R.drawable.bg_primary_button) else setBackgroundColor(Color.WHITE) },LinearLayout.LayoutParams(-1,px(3)))
        }
        if(items.isEmpty()) {
            val empty=LinearLayout(requireContext()).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_HORIZONTAL }
            val space=FrameLayout(requireContext())
            root.addView(space,LinearLayout.LayoutParams(-1,0,1f))
            space.addView(empty,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER).apply { bottomMargin=px(53) })
            empty.addView(ImageView(requireContext()).apply {setImageResource(R.drawable.figma_library_empty)},LinearLayout.LayoutParams(px(109),px(109)))
            empty.addView(label("Nothing Saved Yet"),LinearLayout.LayoutParams(-1,px(22)).apply {topMargin=px(31)})
            empty.addView(label("Add items to your collection",15f,"#9E9E9E"),LinearLayout.LayoutParams(-1,px(20)).apply {topMargin=px(12)})
            // Emoji discovery uses the same Emoji Icon catalog as Status Bar's battery View more.
            empty.addView(label("Explore",16f,"#FFFFFF").apply {setBackgroundResource(R.drawable.bg_primary_button);setOnClickListener {navigate(listOf(ScreenIds.STATUS_MORE_BATTERY_1,ScreenIds.STATUS_MORE_BATTERY_1,ScreenIds.STICKER_MORE,ScreenIds.PET_MORE)[tab])}},LinearLayout.LayoutParams(px(153),px(46)).apply {topMargin=px(12)})
            return
        }
        if(editing) {
            val row=LinearLayout(requireContext()).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(px(18),0,px(18),0) }
            root.addView(row,LinearLayout.LayoutParams(-1,px(42)).apply {topMargin=px(17);bottomMargin=px(13)})
            fun action(title:String, res:Int, width:Int, red:Boolean, click:()->Unit) = label(title,16f,if(red) "#FFFFFF" else "#27205F").apply {
                background=shape(if(red) "#EE413A" else "#FFFFFF",38)
                val d=ResourcesCompat.getDrawable(resources,res,null)!!; d.setBounds(0,0,px(if(red)19 else 23),px(19))
                setCompoundDrawables(d,null,null,null);compoundDrawablePadding=px(6);setPadding(px(if(red)15 else 22),0,px(15),0)
                setOnClickListener {click()}; row.addView(this,LinearLayout.LayoutParams(px(width),-1))
            }
            row.addView(CheckBox(requireContext()).apply {
                text = "All"
                textSize = 16f
                setTextColor(Color.parseColor("#27205F"))
                typeface = ResourcesCompat.getFont(context, R.font.nunito_family)
                background = shape("#FFFFFF",38)
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#27205F"))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(10),0,px(10),0)
                isSaveEnabled = false
                isChecked = items.isNotEmpty() && items.all { it.key in selected }
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.addAll(items.map { it.key }) else selected.clear()
                    render()
                }
            },LinearLayout.LayoutParams(px(93),-1))
            row.addView(View(requireContext()),LinearLayout.LayoutParams(0,1,1f))
            action("Delete (${selected.size})",R.drawable.figma_library_trash,128,true) {showDelete()}.apply { isEnabled=selected.isNotEmpty();alpha=if(isEnabled)1f else .5f }
        }
        val grid=RecyclerView(requireContext()).apply {
            tag = "library_grid"
            layoutManager=GridLayoutManager(context,3);setPadding(px(14),0,px(14),px(14));clipToPadding=false
            adapter=object:RecyclerView.Adapter<LibraryHolder>() {
                override fun getItemCount()=items.size
                override fun onCreateViewHolder(parent:ViewGroup, type:Int):LibraryHolder {
                    val card=FrameLayout(parent.context).apply {background=shape("#F8F7FD",15);clipToOutline=true;layoutParams=RecyclerView.LayoutParams(-1,px(109)).apply {setMargins(px(4),px(4),px(4),px(10))} }
                    return LibraryHolder(card)
                }
                override fun onBindViewHolder(holder:LibraryHolder,position:Int) {holder.bind(items[position])}
                override fun onViewRecycled(holder:LibraryHolder) {com.hd.emojibattery.data.RemoteImageLoader.clear(holder.art)}
            }
        }
        root.addView(grid,LinearLayout.LayoutParams(-1,0,1f).apply {if(!editing)topMargin=px(17)})
        if (scrollState != null) grid.layoutManager?.onRestoreInstanceState(scrollState)
        if(pendingDelete) {
            pendingDelete = false
            showDelete()
        }
    }
    private inner class LibraryHolder(val card:FrameLayout):RecyclerView.ViewHolder(card) {
        val art=ImageView(requireContext()).apply {scaleType=ImageView.ScaleType.FIT_CENTER}
        val mark=ImageView(requireContext())
        init { card.addView(art,FrameLayout.LayoutParams(-1,-1));card.addView(mark,FrameLayout.LayoutParams(px(23),px(23),Gravity.TOP or Gravity.END).apply {topMargin=px(9);rightMargin=px(9)}) }
        fun bind(item:SavedItem) {
            com.hd.emojibattery.data.RemoteImageLoader.clear(art)
            if(item.entry.imageUrl.isNotBlank())com.hd.emojibattery.data.RemoteImageLoader.load(card.context,item.entry.imageUrl,art,null)
            mark.isVisible=editing
            mark.setImageResource(if(item.key in selected)R.drawable.figma_library_checked else 0)
            mark.background=if(item.key in selected)null else shape("#E5D9FF",5)
            card.isSelected=item.key in selected
            card.contentDescription=item.entry.name + if(editing) if(card.isSelected) ", selected" else ", not selected" else ""
            card.setOnClickListener {if(editing){if(!selected.add(item.key))selected.remove(item.key);render()} else open(item)}
        }
    }
    private fun open(item:SavedItem) {
        val args=Bundle().apply {putString("content_id",item.entry.id);putString("content_source",item.source);putString("pet_id",item.entry.id);putString("pet_name",item.entry.name)}
        when(item.source) {
            "battery_styles"->navigate(ScreenIds.STATUS_BAR,args)
            "stickers"->navigate(ScreenIds.ADD_STICKER,args)
            "pets"->navigate(ScreenIds.PET_MORE,args.apply { putString("preselected_pet_id",item.entry.id) })
            "animations","themes"->navigate(ScreenIds.STATUS_BAR,args)
            "charging_animations","wallpapers"->{com.hd.emojibattery.runtime.AppliedContent.select(requireContext(),if(item.source=="charging_animations")"charging" else "wallpaper",item.source,item.entry.id);navigate(if(item.source=="charging_animations")ScreenIds.ANIMATION else ScreenIds.WALLPAPER,args)}
            else->navigate(ScreenIds.STATUS_BAR,args)
        }
    }
    private fun showDelete() {
        if(selected.isEmpty() || deleteDialog?.isShowing==true)return
        val targets=items.filter {it.key in selected}
        val dialog=android.app.Dialog(requireContext())
        val body=LinearLayout(requireContext()).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;background=shape("#FFFFFF",26);setPadding(px(15),px(25),px(15),px(18))}
        body.addView(label("Delete Item?",18f),LinearLayout.LayoutParams(-1,px(28)))
        body.addView(label("This action can’t be undone",16f,"#8F85B5"),LinearLayout.LayoutParams(-1,px(37)).apply {topMargin=px(3)})
        val buttons=LinearLayout(requireContext())
        body.addView(buttons,LinearLayout.LayoutParams(-1,px(58)).apply {topMargin=px(12)})
        buttons.addView(label("Exit",17f).apply {background=shape("#EDE9FD",29);setOnClickListener {dialog.dismiss()}},LinearLayout.LayoutParams(0,-1,1f))
        buttons.addView(label("Delete",16f,"#FFFFFF").apply {background=shape("#EE413A",29);setOnClickListener {
            val edit=prefs.edit();targets.forEach {edit.putBoolean(it.key,false)};edit.apply();selected.clear();dialog.dismiss();render()
        }},LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=px(10)})
        dialog.setContentView(body);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show();dialog.window?.setLayout(minOf(px(369),resources.displayMetrics.widthPixels-px(44)),-2)
        deleteDialog=dialog;trackDialog(dialog)
    }
}
