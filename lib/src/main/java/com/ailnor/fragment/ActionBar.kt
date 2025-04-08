/* 
 * Copyright Erkinjanov Anaskhan, 14/02/2022.
 */

package com.ailnor.fragment

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
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
import com.ailnor.fragment.R
import com.ailnor.core.Theme.alpha
import kotlin.math.max


open class ActionBar(context: Context, navigationType: Int = BACK) : ViewGroup(context) {

    companion object {
        const val SHOW_AS_ACTION_IF_ROOM = 0x1
        const val SHOW_AS_ACTION_ALWAYS = 0x2
        const val SEARCH = 0x4
        const val BADGE = 0x8

        const val NONE = -1
        const val HOME = -2
        const val BACK = -3
        const val CLOSE = -4

        const val ITEM_NAVIGATION = -6
        const val ITEM_OVER_FLOW = -7

        fun getCurrentActionBarHeight(): Int {
            return if (AndroidUtilities.isTablet()) {
                dp(64)
            } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                dp(48)
            } else {
                dp(56)
            }
        }
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
    private var snowflakesEffect: SnowflakesEffect? = null
    private var _occupyStatusBar: Boolean = Build.VERSION.SDK_INT >= 21

    val occupyStatusBar: Boolean
        get() = _occupyStatusBar

    private var contentWithMargin = true

    var drawableRotation: Float = 0f
        set(value) {
            backDrawable.setRotation(value, false)
        }

    val drawableCurrentRotation: Float
        get() = backDrawable.currentRotation

    var drawShadow = true
        set(value) {
            field = value
            (parent as View?)?.invalidate()
        }
    private var adapter: Adapter? = null
    private val activeOverflowItems = arrayListOf<LayoutParams>()
    private val invisibleOverflowItems = arrayListOf<LayoutParams>()

    private var overflowCountWithBadge = 0

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
                        searchCloseButton?.isVisible = s?.isNotEmpty() == true
                    }

                })
        }

    var shouldAddToContainer: Boolean = true

    private var tempNavigationType = 0
    var navigationType: Int = BACK
        set(value) {
            field = value
            if (value == NONE) {
                navigationView?.visibility = GONE
                return
            }
            if (navigationView == null) {
                navigationView = BadgeImageView(context)
                navigationView!!.setPadding(dp(12))
                navigationView!!.background = makeCircleRippleDrawable()
                navigationView!!.setOnClickListener {
                    if (editText?.visibility == VISIBLE) {
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
        isClickable = true
        setBackgroundColor(Theme.colorPrimary)
        this.navigationType = navigationType

        val overFlow = context.resources.getDrawable(R.drawable._ic_over_flow).mutate()
        overFlow.setTint(color)
        overFlowView.setPadding(dp(12))
        overFlowView.setImageDrawable(overFlow)
        overFlowView.background = makeCircleRippleDrawable()
        overFlowView.setOnClickListener {
            showPopup()
        }
        addView(overFlowView)
    }

    fun setOccupyStatusBar(value: Boolean) {
        _occupyStatusBar = value
        setPadding(
            0,
            if (_occupyStatusBar) AndroidUtilities.statusBarHeight else 0,
            0,
            0
        )
    }

    private fun showPopup() {
        listPopup
        adapter!!.clear()
        adapter!!.addAll(activeOverflowItems)
        listPopup.width = measureContentWidth()
        listPopup.show()
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

    fun createMenu(): Builder {
        return Builder.init(this)
    }

    fun setTitle(
        @StringRes res: Int,
        maxLines: Int = 1,
        textSize: Float = 21f
    ): ActionBar {
        return setTitle(context.getString(res), maxLines, textSize)
    }

    open fun setTitle(
        title: CharSequence?,
        maxLines: Int = 1,
        textSize: Float = 21f
    ): ActionBar {
        if (contentView == null) {
            val contentView = TextView(context)
            contentView.setTextColor(color)
            contentView.setTypeface(contentView.typeface, Typeface.BOLD)
            if (navigationType == NONE)
                contentView.setPadding(dp(8), 0, dp(4), 0)
            else
                contentView.setPadding(dp(4), 0, dp(4), 0)
            contentView.ellipsize = TextUtils.TruncateAt.END
            contentWithMargin = true
            this.contentView = contentView
            addView(
                contentView,
                (if (navigationView == null) 0 else 1) + if (editText == null) 0 else 2
            )
        }
        (contentView as TextView).let {
            it.maxLines = maxLines
            it.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
            it.text = title
        }
        return this
    }

    fun setContentView(view: View?, withMargin: Boolean): ActionBar {
        contentWithMargin = withMargin
        if (contentView != null)
            removeView(contentView)
        contentView = view
        addView(
            contentView,
            (if (navigationView == null) 0 else 1) + if (editText == null) 0 else 2
        )
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
                if (isVisible && !layoutParams.showBadge)
                    overflowCountWithBadge++
                else if (!isVisible && layoutParams.showBadge)
                    overflowCountWithBadge--
                overFlowView.isShowing = overflowCountWithBadge != 0
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
        return if (editText?.visibility == VISIBLE) {
            closeSearchMode()
            true
        } else
            false
    }

    fun closeSearchMode() {
        if (tempNavigationType == 0)
            return
        navigationType = tempNavigationType
        searchCloseButton!!.visibility = GONE
        editText!!.hideKeyboard()
        editText!!.visibility = GONE
        contentView?.visibility = VISIBLE
        searchListener?.onSearchCollapsed()
        editText!!.text.clear()
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

            var rightSpace = 0

            if (searchCloseButton!!.visibility == View.VISIBLE) {
                searchCloseButton!!.measure(
                    measureSpec_unspecified,
                    measureSpec_unspecified
                )
                rightSpace += searchCloseButton!!.measuredWidth + searchCloseButton!!.paddingStart + searchCloseButton!!.paddingEnd
            }

            editText!!.measure(
                measureSpec_exactly(width - rightSpace),
                measureSpec_unspecified
            )

            setMeasuredDimension(
                realWidth,
                dp(64)
            )

            inSearchMode = true
        } else
            inSearchMode = false

        overflowCountWithBadge = 0

        var itemsWithAction = 0
        if (inSearchMode) {
            for (i in actionStartIndex until childCount)
                getChildAt(i).visibility = View.GONE
        } else {
            contentView?.measure(
                measureSpec_unspecified,
                measureSpec_unspecified
            )
            var occupiedSpace = 0
            if (contentView != null)
                occupiedSpace = contentView!!.measuredWidth
            else
                realWidth/2
            occupiedSpace += dp(8)

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
                        if (layoutParams.flags and SHOW_AS_ACTION_IF_ROOM != 0) {
                            if (!activeOverflowItems.contains(layoutParams)) {
                                child.visibility = GONE
                                addToOverFlow(layoutParams)
                            } else if (layoutParams.showBadge)
                                overflowCountWithBadge ++
                        } else if (layoutParams.flags and SHOW_AS_ACTION_ALWAYS != 0){
                            child.measure(measureSpec_unspecified, measureSpec_unspecified)
                            width -= child.measuredWidth
                            child.visibility = VISIBLE
                            removeOverFlow(layoutParams)
                            itemsWithAction ++
                        }
                        continue
                    }
                    child.measure(measureSpec_unspecified, measureSpec_unspecified)
                    child.visibility = VISIBLE
                    width -= child.measuredWidth
                    leftSpace = width - child.measuredWidth
                    itemsWithAction ++
                    if (leftSpace <= occupiedSpace) {
                        var index = i
                        while (index >= actionStartIndex){
                            val preChild = getChildAt(index)
                            val preChildParams = preChild.layoutParams as LayoutParams
                            if (preChildParams.isVisible && preChildParams.flags and SHOW_AS_ACTION_IF_ROOM != 0){
                                preChild.visibility = GONE
                                width += preChild.measuredWidth
                                leftSpace += preChild.measuredWidth
                                if (!activeOverflowItems.contains(preChildParams))
                                    addToOverFlow(preChild.layoutParams as LayoutParams)
                                else if (layoutParams.showBadge)
                                    overflowCountWithBadge ++
                                itemsWithAction --
                            }
                            index --
                        }
                        if (leftSpace < dp(48))
                            width -= dp(48) - leftSpace
                        leftSpace = 0
                    } else {
                        removeOverFlow(layoutParams)
                    }
                }
            }
        }

        activeOverflowItems.forEach {
            if (it.flags == 0 && it.showBadge)
                overflowCountWithBadge ++
        }

        if (activeOverflowItems.isNotEmpty() && !inSearchMode) {
            overFlowView.isShowing = overflowCountWithBadge != 0
            overFlowView.visibility = VISIBLE
            overFlowView.measure(
                dp(24),
                dp(24)
            )
            width -= overFlowView.measuredWidth
        } else
            overFlowView.visibility = GONE

        if (inSearchMode)
            contentView?.visibility = GONE
        else
            contentView?.measure(
                measureSpec_exactly(width - if (itemsWithAction > 0) dp(8) else 0),
                measureSpec_unspecified
            )

        setMeasuredDimension(
            realWidth,
            if (contentView != null) {
                if (contentView!!.fitsSystemWindows)
                    max(getCurrentActionBarHeight(), contentView!!.measuredHeight)
                else if (fitsSystemWindows)
                    AndroidUtilities.statusBarHeight + max(
                        getCurrentActionBarHeight(),
                        contentView?.measuredHeight ?: 0
                    )
                else
                    max(
                        getCurrentActionBarHeight(),
                        contentView?.measuredHeight ?: 0
                    )
            } else
                AndroidUtilities.statusBarHeight + getCurrentActionBarHeight()
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var right = measuredWidth - dp(4)
        var left = dp(4)
        val top = if (fitsSystemWindows)
            AndroidUtilities.statusBarHeight
        else
            0

        if (navigationView?.visibility == VISIBLE) {
            navigationView!!.layout(
                left,
                (measuredHeight + top - navigationView!!.measuredHeight) / 2,
                left + navigationView!!.measuredWidth,
                (measuredHeight + top + navigationView!!.measuredHeight) / 2
            )
            left += navigationView!!.measuredWidth
        }

        if (editText?.visibility == VISIBLE) {

            editText!!.layout(
                left,
                (measuredHeight + top - editText!!.measuredHeight) / 2,
                left + editText!!.measuredWidth,
                (measuredHeight + top + editText!!.measuredHeight) / 2
            )

            if (searchCloseButton!!.visibility == VISIBLE)
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
            val top = if (contentView!!.fitsSystemWindows)
                0
            else
                top
            contentView!!.layout(
                left,
                (measuredHeight + top - contentView!!.measuredHeight) / 2,
                left + contentView!!.measuredWidth,
                (measuredHeight + top + contentView!!.measuredHeight) / 2
            )
            indexOfChild(contentView)
        } else
            indexOfChild(navigationView)

        if (editText?.visibility != VISIBLE) {
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

    override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
        val result = super.drawChild(canvas, child, drawingTime)

        if (AndroidUtilities.isWinter && AndroidUtilities.animationEnabled && navigationType == HOME && child == contentView) {
            if (snowflakesEffect == null) {
                snowflakesEffect = SnowflakesEffect(0)
                snowflakesEffect!!.setFitSystemWindows(fitsSystemWindows)
            }
            snowflakesEffect!!.onDraw(this, canvas)
        }

        return result
    }

    private fun addToOverFlow(layoutParams: LayoutParams) {
        activeOverflowItems.add(layoutParams)
        if (layoutParams.showBadge)
            overflowCountWithBadge++
        overFlowView.isShowing = overflowCountWithBadge != 0
        if (listPopup.isShowing) {
            listPopup.dismiss()
            showPopup()
        }
    }

    private fun removeOverFlow(layoutParams: LayoutParams) {
        activeOverflowItems.remove(layoutParams)
        if (layoutParams.showBadge) {
            overflowCountWithBadge--
            if (overflowCountWithBadge < 0)
                overflowCountWithBadge = 0
        }
        overFlowView.isShowing = overflowCountWithBadge != 0
        if (listPopup.isShowing) {
            listPopup.dismiss()
            showPopup()
        }
    }

    fun clear() {
        val firstActionIndex = if (contentView?.visibility == View.VISIBLE)
            indexOfChild(contentView)
        else
            indexOfChild(navigationView)

        activeOverflowItems.clear()
        invisibleOverflowItems.clear()

        overflowCountWithBadge = 0
        overFlowView.isShowing = overflowCountWithBadge != 0

        var current = firstActionIndex + 1
        val last = childCount
        while (current != last) {
            removeViewAt(current)
            current++
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
            if (layoutParams.flags and SEARCH == 0)
                actionListener.onAction(layoutParams.itemId)
            else {
                tempNavigationType = navigationType
                navigationType = BACK
                searchCloseButton!!.isVisible = editText!!.text?.isNotEmpty() == true
                editText!!.visibility = View.VISIBLE
                editText!!.showKeyboard()
                searchListener?.onSearchExpanded()
            }
        }
    }

    fun addOverFlowItem(layoutParams: LayoutParams) {
        if (layoutParams.isVisible)
            addToOverFlow(layoutParams)
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
                removeOverFlow(layoutParams)
            if (layoutParams.flags and SEARCH != 0 && !isVisible && editText != null)
                closeSearchMode()
            requestLayout()
            true
        } else {
            if (isVisible) {
                layoutParams = invisibleOverflowItems.find {
                    it.itemId == itemId
                } ?: return false
                invisibleOverflowItems.remove(layoutParams)
                addToOverFlow(layoutParams)
            } else {
                layoutParams = activeOverflowItems.find {
                    it.itemId == itemId
                } ?: return false
                removeOverFlow(layoutParams)
                invisibleOverflowItems.add(layoutParams)
                if (layoutParams.flags and SEARCH != 0 && editText != null)
                    closeSearchMode()
            }
            requestLayout()
            true
        }
    }

    fun setItemColor(itemId: Int, color: Int): Boolean {
        var layoutParams = activeOverflowItems.find {
            it.itemId == itemId
        }
        if (layoutParams != null) {
            layoutParams.color = color
            if (layoutParams.flags == 0)
                return true
        }
        layoutParams = children.find {
            it.layoutParams is LayoutParams && (it.layoutParams as LayoutParams).itemId == itemId
        }?.layoutParams as? LayoutParams
        return if (layoutParams != null) {
            layoutParams.color = color
            true
        } else
            false
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

    fun setItemTitle(itemId: Int, title: Int): Boolean {
        var layoutParams = activeOverflowItems.find {
            it.itemId == itemId
        }
        if (layoutParams != null) {
            layoutParams.title = title
            if (layoutParams.flags == 0)
                return true
        }
        layoutParams = children.find {
            it.layoutParams is LayoutParams && (it.layoutParams as LayoutParams).itemId == itemId
        }?.layoutParams as? LayoutParams
        return if (layoutParams != null) {
            layoutParams.title = title
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

        fun color(color: Int): Builder {
            item!!.color = color
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
                if (item!!.flags and SEARCH != 0)
                    actionBar!!.addSearch()
            }
            item = null
        }
    }

    class LayoutParams(var itemId: Int) :
        ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT) {

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
                if (view is TextView)
                    (view as TextView).setTextColor(
                        if (field)
                            color
                        else
                            color.alpha(70)
                    )
                else if (view is ImageView)
                    (view as ImageView).setColorFilter(
                        if (field)
                            color
                        else
                            color.alpha(70),
                        PorterDuff.Mode.SRC_IN
                    )
            }
        var showBadge = false
            set(value) {
                field = value
                if (view is BadgeImageView)
                    (view as BadgeImageView).isShowing = field
                else if (view is BadgeTextView)
                    (view as BadgeTextView).isShowing = field
            }
        var color = Theme.white
            set(value) {
                field = value
                if (view is ImageView)
                    (view as ImageView).colorFilter =
                        PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                else if (view is TextView)
                    (view as TextView).setTextColor(color)
            }

        var flags = 0

        fun getView(context: Context, isIcon: Boolean): View {
            return if (isIcon) {
                if (view is ImageView)
                    return view!!
                val imageView = if (flags and BADGE != 0)
                    BadgeImageView(context)
                else
                    ImageView(context)
                imageView.setPadding(dp(12))
                if (icon != 0)
                    imageView.setImageResource(icon)
                imageView.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                imageView.background = makeCircleRippleDrawable()
                if (flags == SEARCH)
                    view?.isVisible = isVisible
                view = imageView
                imageView
            } else {
                if (view is TextView)
                    return view!!
                val textView = if (flags and BADGE != 0)
                    BadgeTextView(context)
                else
                    TextView(context)
                textView.textSize = 14f
                textView.setPadding(dp(8))
                if (title != 0)
                    textView.setText(title)
                textView.setTextColor(
                    if (isEnabled)
                        color
                    else
                        color.alpha(70)
                )
                textView.background = makeRippleDrawable()
                if (flags == SEARCH)
                    view?.isVisible = isVisible
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
