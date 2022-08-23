/* 
 * Copyright Erkinjanov Anaskhan, 14/02/2022.
 */

package com.ailnor.fragment

import android.content.Context
import android.graphics.*
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import com.ailnor.core.*
import com.ailnor.core.R
import com.ailnor.core.Theme.alpha
import kotlin.math.max


open class ActionBar(context: Context, navigationType: Int = BACK) : ViewGroup(context) {

    companion object {
        const val NONE = -1
        const val HOME = -2
        const val BACK = -3
        const val CLOSE = -4

        const val ITEM_NAVIGATION = -6
        const val ITEM_OVER_FLOW = -7
    }

    private val home_Drawable by lazy {
        DrawerArrowDrawable(context)
    }
    private val backDrawable by lazy {
        BackDrawable(false)
    }

    private var navigationView: BadgeImageView? = null
    private var contentView: View? = null
    var editText: EditText? = null
        private set
    private var searchCloseButton: ImageView? = null
    private val overFlowView: BadgeImageView = BadgeImageView(context)
    private var contentWithMargin = true

    var drawableRotation: Float = 0f
    set(value) {
        backDrawable.setRotation(value, false)
    }

    val drawableCurrentRotation: Float
    get() = backDrawable.currentRotation

    var drawShadow = true
    private var adapter: Adapter? = null
    private val activeOverflowItems = arrayListOf<LayoutParams>()
    private val invisibleOverflowItems = arrayListOf<LayoutParams>()

    var color = Theme.white
        set(value) {
            field = value
            if (navigationView != null) {
                if (navigationView!!.drawable is DrawerArrowDrawable)
                    (navigationView!!.drawable as DrawerArrowDrawable).color = color
                else
                    navigationView!!.drawable.setTint(color)
            }
            if (contentView is TextView)
                (contentView as TextView).setTextColor(value)
            overFlowView.colorFilter = PorterDuffColorFilter(value, PorterDuff.Mode.SRC_IN)
        }

    private val listPopup by lazy {
        val popupMenu = ListPopupWindow(context)
        popupMenu.anchorView = overFlowView
        adapter = Adapter(context)
        popupMenu.setOnItemClickListener { _, _, position, _ ->
            if (!isEnabled)
                return@setOnItemClickListener
            popupMenu.dismiss()
            actionListener.onAction(activeOverflowItems[position].itemId)
        }
        popupMenu.setAdapter(adapter)
        popupMenu.verticalOffset = -dp(48)
        popupMenu.isModal = true
        popupMenu
    }

    lateinit var actionListener: ActionListener
    var searchListener: SearchListener? = null
        set(value) {
            field = value
            if (value != null)
                editText!!.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {

                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {

                    }

                    override fun afterTextChanged(s: Editable?) {
                        searchListener?.onQueryTextChangeListener(s?.toString())
                    }

                })
        }

    var shouldAddToContainer: Boolean = true

    private var tempNavigationType = 0
    var navigationType: Int = BACK
        set(value) {
            field = value
            if (value == NONE) {
                navigationView?.visibility = View.GONE
                return
            }
            if (navigationView == null) {
                navigationView = BadgeImageView(context)
                navigationView!!.setPadding(dp(12))
                navigationView!!.background = makeCircleRippleDrawable()
                navigationView!!.setOnClickListener {
                    if (editText?.visibility == View.VISIBLE) {
                        closeSearchMode()
                    } else
                        actionListener.onAction(navigationType)
                }
                addView(navigationView, 0)
            }
            navigationView!!.setImageDrawable(
                if (value == HOME) {
                    home_Drawable.color = color
                    home_Drawable.progress = 0f
                    home_Drawable.color = color
                    home_Drawable
                } else if (value == BACK) {
                    backDrawable.setRotated(true)
                    backDrawable.setColor(color)
                    backDrawable.setRotatedColor(color)
                    backDrawable
                } else {
                    backDrawable.setRotated(false)
                    backDrawable.setColor(color)
                    backDrawable.setRotatedColor(color)
                    backDrawable
                }
            )
        }

    init {
        setBackgroundColor(Theme.colorPrimary)
        this.navigationType = navigationType

        val overFlow = context.resources.getDrawable(R.drawable._ic_over_flow).mutate()
        overFlow.setTint(color)
        overFlowView.setPadding(dp(12))
        overFlowView.setImageDrawable(overFlow)
        overFlowView.background = makeCircleRippleDrawable()
        overFlowView.setOnClickListener {
            listPopup
            adapter!!.clear()
            adapter!!.addAll(activeOverflowItems)
            listPopup.width = measureContentWidth()
            listPopup.show()
        }
        addView(overFlowView)
    }

    private fun measureContentWidth(): Int {
        var mMeasureParent: ViewGroup? = null
        var maxWidth = 0
        var itemView: View? = null
        var itemType = 0
        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val count = adapter!!.count
        for (i in 0 until count) {
            val positionType = adapter!!.getItemViewType(i)
            if (positionType != itemType) {
                itemType = positionType
                itemView = null
            }
            if (mMeasureParent == null)
                mMeasureParent = FrameLayout(context)
            itemView = adapter!!.getView(i, itemView, mMeasureParent)
            itemView.measure(widthMeasureSpec, heightMeasureSpec)
            val itemWidth = itemView.measuredWidth
            if (itemWidth > maxWidth)
                maxWidth = itemWidth
        }
        return max(maxWidth, dp(200))
    }

    fun createMenu(): Builder{
        return Builder.init(this)
    }

    fun setTitle(@StringRes res: Int): ActionBar{
        setTitle(context.getString(res))
        return this
    }

    fun setTitle(title: String?): ActionBar{
        if (contentView == null) {
            val contentView = TextView(context)
            contentView.textSize = 20f
            contentView.setTextColor(color)
            contentView.setTypeface(contentView.typeface, Typeface.BOLD)
            contentView.setPadding(dp(4), 0, dp(4), 0)
            contentView.maxLines = 1
            contentView.ellipsize = TextUtils.TruncateAt.END
            contentWithMargin = true
            this.contentView = contentView
            addView(
                contentView,
                (if (navigationView == null) 0 else 1) + if (editText == null) 0 else 2
            )
        }
        (contentView as TextView).text = title
        return this
    }

    fun setContentView(view: View?, withMargin: Boolean): ActionBar{
        contentWithMargin = withMargin
        if (contentView != null)
            removeView(contentView)
        contentView = view
        addView(contentView, if (navigationView == null) 0 else 1)
        return this
    }

    fun setBadgeVisibility(itemId: Int, isVisible: Boolean) {
        if (itemId == ITEM_OVER_FLOW)
            overFlowView.isShowing = isVisible
        else if (itemId == ITEM_NAVIGATION)
            navigationView?.isShowing = isVisible
        else {
            val layoutParams = activeOverflowItems.find {
                it.itemId == itemId
            }
            if (layoutParams != null) {
                layoutParams.showBadge = isVisible
                if (layoutParams.flags == 0)
                    return
            }
            (children.find {
                it.layoutParams is LayoutParams && (it.layoutParams as LayoutParams).itemId == itemId
            }?.layoutParams as? LayoutParams)?.showBadge = isVisible
        }
    }

    fun onBackPressed(): Boolean {
        return if (editText?.visibility == View.VISIBLE) {
            closeSearchMode()
            true
        } else
            false
    }

    private fun closeSearchMode() {
        navigationType = tempNavigationType
        searchCloseButton!!.visibility = View.GONE
        editText!!.hideKeyboard()
        editText!!.visibility = View.GONE
        contentView?.visibility = View.VISIBLE
        searchListener?.onSearchCollapsed()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val realWidth = MeasureSpec.getSize(widthMeasureSpec)
        var width = realWidth
        var actionStartIndex = if (editText != null)
            3
        else if (contentView != null)
            1
        else
            0

        if (navigationView?.visibility == View.VISIBLE) {
            navigationView!!.measure(
                measureSpec_unspecified,
                measureSpec_unspecified
            )
            width -= navigationView!!.measuredWidth
            actionStartIndex++
        }

        val inSearchMode: Boolean
        if (editText?.visibility == View.VISIBLE) {

            searchCloseButton!!.measure(
                measureSpec_unspecified,
                measureSpec_unspecified
            )

            editText!!.measure(
                measureSpec_exactly(width - searchCloseButton!!.measuredWidth - searchCloseButton!!.paddingStart - searchCloseButton!!.paddingEnd),
                measureSpec_unspecified
            )

            setMeasuredDimension(
                realWidth,
                dp(64)
            )

            inSearchMode = true
        } else
            inSearchMode = false

        if (inSearchMode) {
            for (i in actionStartIndex until childCount)
                getChildAt(i).visibility = View.GONE
        } else {
            var leftSpace = -1
            for (i in actionStartIndex until childCount) {
                val child = getChildAt(i)
                val layoutParams = child.layoutParams
                if (layoutParams is LayoutParams) {
                    if (!layoutParams.isVisible) {
                        child.visibility = GONE
                        continue
                    }
                    if (leftSpace == 0) {
                        child.visibility = GONE
                        if (layoutParams.flags and LayoutParams.SHOW_AS_ACTION_IF_ROOM != 0 && !activeOverflowItems.contains(
                                layoutParams
                            )
                        )
                            activeOverflowItems.add(layoutParams)
                        continue
                    }
                    child.measure(measureSpec_unspecified, measureSpec_unspecified)
                    leftSpace = width - child.measuredWidth
                    if (leftSpace <= realWidth / 2) {
                        if (i != actionStartIndex) {
                            var index = actionStartIndex + 1
                            while (leftSpace < dp(48) && index < childCount) {
                                val preChild = getChildAt(i - 1)
                                preChild.visibility = View.GONE
                                val preChildParams = preChild.layoutParams as LayoutParams
                                if (preChildParams.flags and LayoutParams.SHOW_AS_ACTION_ALWAYS == 0 && !activeOverflowItems.contains(preChildParams))
                                    activeOverflowItems.add(preChild.layoutParams as LayoutParams)
                                leftSpace += preChild.measuredWidth
                                index++
                            }
                            if (leftSpace < dp(48))
                                width -= dp(48) - leftSpace
                        }
                        if (layoutParams.flags and LayoutParams.SHOW_AS_ACTION_IF_ROOM != 0) {
                            child.visibility = GONE
                            if (!activeOverflowItems.contains(layoutParams))
                                activeOverflowItems.add(layoutParams)
                        }
                        leftSpace = 0
                    } else {
                        width -= child.measuredWidth
                        child.visibility = VISIBLE
                        activeOverflowItems.remove(layoutParams)
                    }
                }
            }
        }

        if (activeOverflowItems.isNotEmpty() && !inSearchMode) {
            overFlowView.visibility = View.VISIBLE
            overFlowView.measure(
                dp(24),
                dp(24)
            )
            width -= overFlowView.measuredWidth
        } else
            overFlowView.visibility = View.GONE

        if (inSearchMode)
            contentView?.visibility = View.GONE
        else
            contentView?.measure(
                measureSpec_exactly(width),
                measureSpec_unspecified
            )

        setMeasuredDimension(
            realWidth,
            Utilities.statusBarHeight + max(dp(64), contentView?.measuredHeight ?: 0)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var right = measuredWidth - dp(4)
        var left = dp(4)
        val top = Utilities.statusBarHeight

        if (navigationView?.visibility == View.VISIBLE) {
            navigationView!!.layout(
                left,
                (measuredHeight + top - navigationView!!.measuredHeight) / 2,
                left + navigationView!!.measuredWidth,
                (measuredHeight + top + navigationView!!.measuredHeight) / 2
            )
            left += navigationView!!.measuredWidth
        }

        if (editText?.visibility == View.VISIBLE) {

            editText!!.layout(
                left,
                (measuredHeight + top - editText!!.measuredHeight) / 2,
                left + editText!!.measuredWidth,
                (measuredHeight + top + editText!!.measuredHeight) / 2
            )

            searchCloseButton!!.layout(
                editText!!.right,
                (measuredHeight + top - searchCloseButton!!.measuredHeight) / 2,
                editText!!.right + searchCloseButton!!.measuredWidth,
                (measuredHeight + top + searchCloseButton!!.measuredHeight) / 2
            )
        }

        val firstActionIndex = if (contentView?.visibility == View.VISIBLE) {
            val left = if (contentWithMargin)
                left
            else
                (left - dp(4))
            contentView?.layout(
                left,
                (measuredHeight + top - contentView!!.measuredHeight) / 2,
                left + contentView!!.measuredWidth,
                (measuredHeight + top + contentView!!.measuredHeight) / 2
            )
            indexOfChild(contentView)
        } else
            indexOfChild(navigationView)

        if (editText?.visibility != View.VISIBLE) {
            for (i in childCount - 1 downTo firstActionIndex + 1) {
                val child = getChildAt(i)
                if (child.visibility != View.VISIBLE)
                    continue
                child.layout(
                    right - child.measuredWidth,
                    (measuredHeight + top - child.measuredHeight) / 2,
                    right,
                    (measuredHeight + top + child.measuredHeight) / 2
                )
                right = child.left
            }
        }
    }

    @Deprecated("User ActionBar.Builder")
    fun addMenuItem(
        itemId: Int,
        iconRes: Int,
        titleRes: Int = 0,
        flags: Int = 0
    ): LayoutParams {
        return Builder.init(this)
            .createItem(itemId, flags)
            .title(titleRes)
            .icon(iconRes)
            .temp_build()
        val layoutParams = LayoutParams(itemId)
        layoutParams.title = titleRes
        layoutParams.icon = iconRes
        layoutParams.flags = flags
        if (flags == 0)
            activeOverflowItems.add(layoutParams)
        else {
            val view = layoutParams.getView(context, iconRes != 0)
            view.setOnClickListener {
                if (flags and LayoutParams.SEARCH == 0)
                    actionListener.onAction(itemId)
                else {
                    searchCloseButton!!.visibility = View.VISIBLE
                    editText!!.visibility = View.VISIBLE
                    editText!!.showKeyboard()
                    searchListener?.onSearchExpanded()
                }
            }
            layoutParams.flags = flags
            addView(view, childCount - 1, layoutParams)

            if (flags and LayoutParams.SEARCH != 0) {

                editText = EditText(context)
                editText!!.background = null
                editText!!.visibility = View.GONE
                editText!!.setTextColor(Theme.black)
                editText!!.setHintTextColor(Theme.black.alpha(30))

                searchCloseButton = ImageView(context)
                searchCloseButton!!.setPadding(dp(12))
                searchCloseButton!!.background = makeCircleRippleDrawable()
                searchCloseButton!!.setOnClickListener {
                    editText!!.text.clear()
                }
                searchCloseButton!!.setImageDrawable(
                    R.drawable._ic_cross.coloredDrawable(
                        Theme.black
                    )
                )

                addView(searchCloseButton, 0)
                addView(editText, 0)
            }
        }
    }

    fun addSearch() {
        editText = EditText(context)
        editText!!.background = null
        editText!!.visibility = View.GONE
        editText!!.setTextColor(Theme.white)
        editText!!.setHintTextColor(Theme.white.alpha(60))

        searchCloseButton = ImageView(context)
        searchCloseButton!!.setPadding(dp(12))
        searchCloseButton!!.background = makeCircleRippleDrawable()
        searchCloseButton!!.setOnClickListener {
            editText!!.text.clear()
        }
        searchCloseButton!!.setImageDrawable(R.drawable._ic_cross.drawable())

        addView(searchCloseButton, 0)
        addView(editText, 0)
    }

    fun setItemClickListener(view: View, layoutParams: LayoutParams) {
        view.setOnClickListener {
            if (!isEnabled)
                return@setOnClickListener
            if (layoutParams.flags and LayoutParams.SEARCH == 0)
                actionListener.onAction(layoutParams.itemId)
            else {
                tempNavigationType = navigationType
                navigationType = BACK
                searchCloseButton!!.visibility = View.VISIBLE
                editText!!.visibility = View.VISIBLE
                editText!!.showKeyboard()
                searchListener?.onSearchExpanded()
            }
        }
    }

    fun addOverFlowItem(layoutParams: LayoutParams) {
        if (layoutParams.isVisible)
            activeOverflowItems.add(layoutParams)
        else
            invisibleOverflowItems.add(layoutParams)
    }


    fun setItemVisibility(itemId: Int, isVisible: Boolean): Boolean {
        var layoutParams = children.find {
            it.layoutParams is LayoutParams && (it.layoutParams as LayoutParams).itemId == itemId
        }?.layoutParams as? LayoutParams

        return if (layoutParams != null) {
            layoutParams.isVisible = isVisible
            if (!isVisible && activeOverflowItems.contains(layoutParams))
                activeOverflowItems.remove(layoutParams)
            requestLayout()
            true
        } else {
            if (isVisible) {
                layoutParams = invisibleOverflowItems.find {
                    it.itemId == itemId
                } ?: return false
                invisibleOverflowItems.remove(layoutParams)
                activeOverflowItems.add(layoutParams)
            } else {
                layoutParams = activeOverflowItems.find {
                    it.itemId == itemId
                } ?: return false
                activeOverflowItems.remove(layoutParams)
                invisibleOverflowItems.add(layoutParams)
            }
            requestLayout()
            true
        }
    }

//    fun setItemVisibility(itemId: Int, isVisible: Boolean): Boolean {
//        var layoutParams = activeOverflowItems.find {
//            it.itemId == itemId
//        }
//        if (layoutParams != null) {
//            layoutParams.isVisible = isVisible
//            if (layoutParams.flags == 0)
//                return true
//        }
//        layoutParams = children.find {
//            it.layoutParams is LayoutParams && (it.layoutParams as LayoutParams).itemId == itemId
//        }?.layoutParams as? LayoutParams
//        return if (layoutParams != null) {
//            layoutParams.isVisible = isVisible
//            true
//        } else
//            false
//    }

    fun setItemEnabled(itemId: Int, isEnabled: Boolean): Boolean {
        var layoutParams = activeOverflowItems.find {
            it.itemId == itemId
        }
        if (layoutParams != null) {
            layoutParams.isEnabled = isEnabled
            if (layoutParams.flags == 0)
                return true
        }
        layoutParams = children.find {
            it.layoutParams is LayoutParams && (it.layoutParams as LayoutParams).itemId == itemId
        }?.layoutParams as? LayoutParams
        return if (layoutParams != null) {
            layoutParams.isEnabled = isEnabled
            true
        } else
            false
    }

    fun setItemIcon(itemId: Int, icon: Int): Boolean {
        var layoutParams = activeOverflowItems.find {
            it.itemId == itemId
        }
        if (layoutParams != null) {
            layoutParams.icon = icon
            if (layoutParams.flags == 0)
                return true
        }
        layoutParams = children.find {
            it.layoutParams is LayoutParams && (it.layoutParams as LayoutParams).itemId == itemId
        }?.layoutParams as? LayoutParams
        return if (layoutParams != null) {
            layoutParams.icon = icon
            true
        } else
            false
    }

    class Adapter(context: Context) : ArrayAdapter<LayoutParams>(context, 0, arrayListOf()) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = getItem(position)
            val textView = if (item?.showBadge == true) {
                val badgeTextView = BadgeTextView(context)
                badgeTextView.isShowing = true
                badgeTextView
            } else
                TextView(context)
            if (item?.title != null)
                textView.setText(item.title)
            textView.maxLines = 1
            textView.setPadding(dp(8))
            return textView
        }
    }

    class BadgeImageView(context: Context) : ImageView(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var isShowing = false
            set(value) {
                field = value
                invalidate()
            }

        init {
            paint.style = Paint.Style.FILL
            paint.color = Theme.red
        }

        override fun onDrawForeground(canvas: Canvas) {
            super.onDrawForeground(canvas)
            if (isShowing)
                canvas.drawCircle(width * 0.75f, height * 0.25f, dp(3f), paint)
        }

    }

    class BadgeTextView(context: Context) : TextView(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var isShowing = false
            set(value) {
                field = value
                invalidate()
            }

        init {
            paint.style = Paint.Style.FILL
            paint.color = Theme.red
        }

        override fun onDrawForeground(canvas: Canvas) {
            super.onDrawForeground(canvas)
            if (isShowing)
                canvas.drawCircle(width * 0.9f, height * 0.5f, dp(2.5f), paint)
        }

    }


    class Builder {

        private var item: LayoutParams? = null
        private var actionBar: ActionBar? = null

        companion object {
            private val instance = Builder()

            fun init(actionBar: ActionBar): Builder {
                instance.actionBar = actionBar
                return instance
            }
        }

        fun createItem(id: Int, flags: Int = 0): Builder {
            if (item != null)
                build()
            init(id, flags)
            return this
        }

        private fun init(id: Int, flags: Int) {
            item = LayoutParams(id)
            item!!.flags = flags
        }

        fun title(title: Int): Builder {
            item!!.title = title
            return this
        }

        fun icon(icon: Int): Builder {
            item!!.icon = icon
            return this
        }

        fun enabled(enabled: Boolean): Builder {
            item!!.isEnabled = enabled
            return this
        }

        fun visibility(visible: Boolean): Builder {
            item!!.isVisible = visible
            return this
        }

        fun build() {
            if (item!!.flags == 0)
                actionBar!!.addOverFlowItem(item!!)
            else {
                val view = item!!.getView(actionBar!!.context, item!!.icon != 0)
                actionBar!!.setItemClickListener(view, item!!)
                actionBar!!.addView(
                    view,
                    actionBar!!.childCount - 1,
                    item
                )
                if (item!!.flags and LayoutParams.SEARCH != 0)
                    actionBar!!.addSearch()
            }
            item = null
        }

        internal fun temp_build(): LayoutParams {
            val temp = item!!
            build()
            return temp
        }
    }

    class LayoutParams(var itemId: Int) :
        ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT) {
        companion object {
            const val SHOW_AS_ACTION_IF_ROOM = 0b1
            const val SHOW_AS_ACTION_ALWAYS = 0b10
            const val SEARCH = 0b100
        }

        private var view: View? = null
        var icon: Int = 0
            set(value) {
                field = value
                if (view is ImageView && icon != 0)
                    (view as ImageView).setImageResource(icon)
            }
        var title: Int = 0
            set(value) {
                field = value
                if (view is TextView && title != 0)
                    (view as TextView).setText(value)
            }
        var isVisible = true
            set(value) {
                field = value
                if (flags != 0 || flags == SEARCH)
                    view?.isVisible = field
            }
        var isEnabled = true
            set(value) {
                field = value
                view?.isEnabled = field
            }
        var showBadge = false
            set(value) {
                field = value
                if (view is BadgeImageView)
                    (view as BadgeImageView).isShowing = field
                else if (view is BadgeTextView)
                    (view as BadgeTextView).isShowing = field
            }

        var flags = 0

        fun getView(context: Context, isIcon: Boolean): View {
            return if (isIcon) {
                if (view is ImageView)
                    return view!!
                val imageView = ImageView(context)
                imageView.setPadding(dp(12))
                if (icon != 0)
                    imageView.setImageResource(icon)
                imageView.colorFilter = PorterDuffColorFilter(Theme.white, PorterDuff.Mode.SRC_IN)
                imageView.background = makeCircleRippleDrawable()
                view = imageView
                imageView
            } else {
                if (view is TextView)
                    return view!!
                val textView = TextView(context)
                textView.textSize = 14f
                textView.setPadding(dp(8))
                if (title != 0)
                    textView.setText(title)
                textView.setTextColor(Theme.white)
                textView.background = makeRippleDrawable()
                view = textView
                textView
            }
        }

    }

    interface ActionListener {
        fun onAction(action: Int)
    }

    interface SearchListener {
        fun onQueryTextChangeListener(query: String?)
        fun onSearchExpanded()
        fun onSearchCollapsed()
    }
}
