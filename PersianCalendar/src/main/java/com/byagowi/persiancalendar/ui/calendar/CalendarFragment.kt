package com.byagowi.persiancalendar.ui.calendar

import android.Manifest
import android.animation.LayoutTransition
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.SearchAutoComplete
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.byagowi.persiancalendar.LANG_FA
import com.byagowi.persiancalendar.LAST_CHOSEN_TAB_KEY
import com.byagowi.persiancalendar.PREF_APP_LANGUAGE
import com.byagowi.persiancalendar.PREF_DISABLE_OWGHAT
import com.byagowi.persiancalendar.PREF_HOLIDAY_TYPES
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.ReleaseDebugDifference.debugAssertNotNull
import com.byagowi.persiancalendar.databinding.EventsTabContentBinding
import com.byagowi.persiancalendar.databinding.FragmentCalendarBinding
import com.byagowi.persiancalendar.databinding.OwghatTabContentBinding
import com.byagowi.persiancalendar.databinding.OwghatTabPlaceholderBinding
import com.byagowi.persiancalendar.entities.CalendarEvent
import com.byagowi.persiancalendar.ui.DrawerHost
import com.byagowi.persiancalendar.ui.calendar.calendarpager.CalendarPager
import com.byagowi.persiancalendar.ui.calendar.dialogs.showDayPickerDialog
import com.byagowi.persiancalendar.ui.calendar.dialogs.showMonthOverviewDialog
import com.byagowi.persiancalendar.ui.calendar.dialogs.showShiftWorkDialog
import com.byagowi.persiancalendar.ui.calendar.searchevent.SearchEventsAdapter
import com.byagowi.persiancalendar.ui.preferences.INTERFACE_CALENDAR_TAB
import com.byagowi.persiancalendar.ui.preferences.LOCATION_ATHAN_TAB
import com.byagowi.persiancalendar.ui.shared.CalendarsView
import com.byagowi.persiancalendar.utils.Jdn
import com.byagowi.persiancalendar.utils.allEnabledEvents
import com.byagowi.persiancalendar.utils.appPrefs
import com.byagowi.persiancalendar.utils.askForCalendarPermission
import com.byagowi.persiancalendar.utils.calculationMethod
import com.byagowi.persiancalendar.utils.calendarType
import com.byagowi.persiancalendar.utils.coordinates
import com.byagowi.persiancalendar.utils.dayTitleSummary
import com.byagowi.persiancalendar.utils.emptyEventsStore
import com.byagowi.persiancalendar.utils.formatDeviceCalendarEventTitle
import com.byagowi.persiancalendar.utils.formatNumber
import com.byagowi.persiancalendar.utils.getA11yDaySummary
import com.byagowi.persiancalendar.utils.getAllEnabledAppointments
import com.byagowi.persiancalendar.utils.getCityName
import com.byagowi.persiancalendar.utils.getCompatDrawable
import com.byagowi.persiancalendar.utils.getEnabledCalendarTypes
import com.byagowi.persiancalendar.utils.getEvents
import com.byagowi.persiancalendar.utils.getEventsTitle
import com.byagowi.persiancalendar.utils.getShiftWorkTitle
import com.byagowi.persiancalendar.utils.isHighTextContrastEnabled
import com.byagowi.persiancalendar.utils.isShowDeviceCalendarEvents
import com.byagowi.persiancalendar.utils.isTalkBackEnabled
import com.byagowi.persiancalendar.utils.language
import com.byagowi.persiancalendar.utils.logException
import com.byagowi.persiancalendar.utils.mainCalendar
import com.byagowi.persiancalendar.utils.monthName
import com.byagowi.persiancalendar.utils.navigateSafe
import com.byagowi.persiancalendar.utils.onClick
import com.byagowi.persiancalendar.utils.readDayDeviceEvents
import com.byagowi.persiancalendar.utils.toJavaCalendar
import com.cepmuvakkit.times.posAlgo.SunMoonPosition
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import io.github.persiancalendar.praytimes.PrayTimesCalculator

private const val CALENDARS_TAB = 0
private const val EVENTS_TAB = 1
private const val OWGHAT_TAB = 2

class CalendarFragment : Fragment() {

    private var mainBinding: FragmentCalendarBinding? = null
    private var calendarsView: CalendarsView? = null
    private var owghatBinding: OwghatTabContentBinding? = null
    private var eventsBinding: EventsTabContentBinding? = null
    private var searchView: SearchView? = null
    private var todayButton: MenuItem? = null
    private val initialDate = Jdn.today.toCalendar(mainCalendar)

    override fun onDestroyView() {
        super.onDestroyView()
        mainBinding = null
        calendarsView = null
        owghatBinding = null
        eventsBinding = null
        searchView = null
        todayButton = null
    }

    private val onBackPressedCloseSearchCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            searchView?.takeIf { !it.isIconified }?.onActionViewCollapsed()
            isEnabled = false
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity?.onBackPressedDispatcher?.addCallback(this, onBackPressedCloseSearchCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = FragmentCalendarBinding.inflate(inflater, container, false).also { binding ->
        mainBinding = binding

        val appPrefs = inflater.context.appPrefs
        val shouldDisableOwghatTab =
            coordinates == null && // if coordinates is set, should be shown
                    (language != LANG_FA || // The placeholder isn't translated to other languages
                            // The user is already dismissed the third tab
                            appPrefs.getBoolean(PREF_DISABLE_OWGHAT, false) ||
                            // Try to not show the placeholder to established users
                            PREF_APP_LANGUAGE in appPrefs)

        val tabs = listOf(
            // First tab
            R.string.calendar to CalendarsView(inflater.context).also { this.calendarsView = it },

            // Second tab
            R.string.events to EventsTabContentBinding.inflate(
                inflater, container, false
            ).also { eventsBinding ->
                this.eventsBinding = eventsBinding
                eventsBinding.eventsContent.layoutTransition = LayoutTransition().also {
                    it.enableTransitionType(LayoutTransition.CHANGING)
                    it.setAnimateParentHierarchy(false)
                }
            }.root
        ) + if (shouldDisableOwghatTab) emptyList() else listOf(
            // The optional third tab
            R.string.owghat to if (coordinates == null) {
                OwghatTabPlaceholderBinding.inflate(
                    inflater, container, false
                ).also { owghatBindingPlaceholder ->
                    owghatBindingPlaceholder.activate.setOnClickListener {
                        findNavController().navigateSafe(
                            CalendarFragmentDirections.navigateToSettings(LOCATION_ATHAN_TAB)
                        )
                    }
                    owghatBindingPlaceholder.discard.setOnClickListener {
                        context?.appPrefs?.edit { putBoolean(PREF_DISABLE_OWGHAT, true) }
                        findNavController().navigateSafe(
                            CalendarFragmentDirections.navigateToSelf()
                        )
                    }
                }.root
            } else {
                OwghatTabContentBinding.inflate(
                    inflater, container, false
                ).also { owghatBinding ->
                    this.owghatBinding = owghatBinding

                    var isExpanded = false
                    owghatBinding.root.setOnClickListener {
                        isExpanded = !isExpanded
                        owghatBinding.timesFlow.toggle()
                        owghatBinding.moreOwghat.contentDescription = resources.getString(
                            if (isExpanded) R.string.close else R.string.open
                        )
                        owghatBinding.moreOwghat.animate()
                            .rotation(if (isExpanded) 180f else 0f)
                            .setDuration(
                                resources.getInteger(android.R.integer.config_shortAnimTime)
                                    .toLong()
                            )
                            .start()
                    }
                    owghatBinding.cityName.also {
                        val cityName = getCityName(it.context, false)
                        if (cityName.isNotEmpty()) it.text = cityName
                    }
                    owghatBinding.times.layoutTransition = LayoutTransition().also {
                        it.enableTransitionType(LayoutTransition.APPEARING)
                        it.setAnimateParentHierarchy(false)
                    }
                    owghatBinding.timesFlow.setup()
                }.root
            }
        )

        // tabs should fill their parent otherwise view pager can't handle it
        tabs.forEach { (_: Int, tabView: ViewGroup) ->
            tabView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        binding.calendarPager.also {
            it.onDayClicked = fun(jdn: Jdn) { bringDate(jdn, monthChange = false) }
            it.onDayLongClicked = fun(jdn: Jdn) { addEventOnCalendar(jdn) }
            it.onMonthSelected = fun() {
                it.selectedMonth.let { date ->
                    updateToolbar(date.monthName, formatNumber(date.year))
                    todayButton?.isVisible =
                        date.year != initialDate.year || date.month != initialDate.month
                }
            }
        }

        binding.viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount(): Int = tabs.size
            override fun getItemViewType(position: Int) = position // set viewtype equal to position
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object : RecyclerView.ViewHolder(tabs[viewType].second) {}
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, i ->
            tab.setText(tabs[i].first)
        }.attach()
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == OWGHAT_TAB) owghatBinding?.sunView?.startAnimate()
                else owghatBinding?.sunView?.clear()
                context?.appPrefs?.edit { putInt(LAST_CHOSEN_TAB_KEY, position) }
            }
        })

        var lastTab = inflater.context.appPrefs.getInt(LAST_CHOSEN_TAB_KEY, CALENDARS_TAB)
        if (lastTab >= tabs.size) lastTab = CALENDARS_TAB
        binding.viewPager.setCurrentItem(lastTab, false)
        setupMenu(binding.appBar.toolbar, binding.calendarPager)
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bringDate(Jdn.today, monthChange = false, highlight = false)

        mainBinding?.let {
            (activity as? DrawerHost)?.setupToolbarWithDrawer(viewLifecycleOwner, it.appBar.toolbar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                it.appBar.appbarLayout.outlineProvider = null
        }

        Jdn.today.toCalendar(mainCalendar).let { today ->
            updateToolbar(today.monthName, formatNumber(today.year))
        }
    }

    private fun addEventOnCalendar(jdn: Jdn) {
        val activity = activity ?: return
        if (ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) askForCalendarPermission(activity) else {
            runCatching { addEvent.launch(jdn) }.onFailure(logException).onFailure {
                Snackbar.make(
                    mainBinding?.root ?: return, R.string.device_calendar_does_not_support,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateToolbar(title: String, subTitle: String) {
        mainBinding?.appBar?.toolbar?.let {
            it.title = title
            it.subtitle = subTitle
        }
    }

    private val addEvent =
        registerForActivityResult(object : ActivityResultContract<Jdn, Void>() {
            override fun parseResult(resultCode: Int, intent: Intent?): Void? = null
            override fun createIntent(context: Context, jdn: Jdn): Intent {
                val time = jdn.toJavaCalendar().timeInMillis
                return Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(
                        CalendarContract.Events.DESCRIPTION, dayTitleSummary(
                            jdn, jdn.toCalendar(mainCalendar)
                        )
                    )
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, time)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, time)
                    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            }
        }) { mainBinding?.calendarPager?.refresh(isEventsModified = true) }

    private val viewEvent =
        registerForActivityResult(object : ActivityResultContract<Long, Void>() {
            override fun parseResult(resultCode: Int, intent: Intent?): Void? = null
            override fun createIntent(context: Context, id: Long): Intent =
                Intent(Intent.ACTION_VIEW).setData(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                )
        }) { mainBinding?.calendarPager?.refresh(isEventsModified = true) }

    override fun onResume() {
        super.onResume()
        // If events are enabled refresh the pager events on resumes anyway
        if (isShowDeviceCalendarEvents) mainBinding?.calendarPager?.refresh(isEventsModified = true)
    }

    private fun getDeviceEventsTitle(dayEvents: List<CalendarEvent<*>>) = buildSpannedString {
        dayEvents.filterIsInstance<CalendarEvent.DeviceCalendarEvent>().forEachIndexed { i, event ->
            if (i != 0) append("\n")
            inSpans(object : ClickableSpan() {
                override fun onClick(textView: View) = runCatching {
                    viewEvent.launch(event.id.toLong())
                }.onFailure(logException).onFailure {
                    Snackbar.make(
                        textView,
                        R.string.device_calendar_does_not_support,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }.let {}

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    runCatching {
                        // should be turned to long then int otherwise gets stupid alpha
                        if (event.color.isNotEmpty()) ds.color = event.color.toLong().toInt()
                    }.onFailure(logException)
                }
            }) { append(formatDeviceCalendarEventTitle(event)) }
        }
    }

    private var selectedJdn = Jdn.today

    private fun bringDate(jdn: Jdn, highlight: Boolean = true, monthChange: Boolean = true) {
        selectedJdn = jdn

        mainBinding?.calendarPager?.setSelectedDay(jdn, highlight, monthChange)

        val isToday = Jdn.today == jdn

        // Show/Hide bring today menu button
        todayButton?.isVisible = !isToday

        // Update tabs
        calendarsView?.showCalendars(jdn, mainCalendar, getEnabledCalendarTypes())
        showEvent(jdn)
        setOwghat(jdn, isToday)

        // a11y
        if (isTalkBackEnabled && !isToday && monthChange) Snackbar.make(
            mainBinding?.root ?: return,
            getA11yDaySummary(
                context ?: return, jdn, false, emptyEventsStore(),
                withZodiac = true, withOtherCalendars = true, withTitle = true
            ),
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showEvent(jdn: Jdn) {
        val activity = activity ?: return
        val eventsBinding = eventsBinding ?: return

        eventsBinding.shiftWorkTitle.text = getShiftWorkTitle(jdn, false)
        val events = jdn.getEvents(jdn.readDayDeviceEvents(activity))
        val holidays = getEventsTitle(
            events,
            holiday = true, compact = false, showDeviceCalendarEvents = false, insertRLM = false,
            addIsHoliday = isHighTextContrastEnabled
        )
        val nonHolidays = getEventsTitle(
            events,
            holiday = false, compact = false, showDeviceCalendarEvents = false, insertRLM = false,
            addIsHoliday = false
        )
        val deviceEvents = getDeviceEventsTitle(events)
        val contentDescription = StringBuilder()

        eventsBinding.eventMessage.isVisible = false
        eventsBinding.noEvent.isVisible = true

        if (holidays.isNotEmpty()) {
            eventsBinding.noEvent.isVisible = false
            eventsBinding.holidayTitle.text = holidays
            val holidayContent = getString(R.string.holiday_reason) + "\n" + holidays
            eventsBinding.holidayTitle.contentDescription = holidayContent
            contentDescription.append(holidayContent)
            eventsBinding.holidayTitle.isVisible = true
        } else {
            eventsBinding.holidayTitle.isVisible = false
        }

        if (deviceEvents.isNotEmpty()) {
            eventsBinding.noEvent.isVisible = false
            eventsBinding.deviceEventTitle.text = deviceEvents
            contentDescription
                .append("\n")
                .append(getString(R.string.show_device_calendar_events))
                .append("\n")
                .append(deviceEvents)

            eventsBinding.deviceEventTitle.let {
                it.movementMethod = LinkMovementMethod.getInstance()
                it.isVisible = true
            }
        } else {
            eventsBinding.deviceEventTitle.isVisible = false
        }

        if (nonHolidays.isNotEmpty()) {
            eventsBinding.noEvent.isVisible = false
            eventsBinding.eventTitle.text = nonHolidays
            contentDescription
                .append("\n")
                .append(getString(R.string.events))
                .append("\n")
                .append(nonHolidays)

            eventsBinding.eventTitle.isVisible = true
        } else {
            eventsBinding.eventTitle.isVisible = false
        }

        val messageToShow = SpannableStringBuilder()

        val enabledTypes = activity.appPrefs
            .getStringSet(PREF_HOLIDAY_TYPES, null) ?: emptySet()
        if (enabledTypes.isEmpty()) {
            eventsBinding.noEvent.isVisible = false
            if (messageToShow.isNotEmpty()) messageToShow.append("\n")

            val title = getString(R.string.warn_if_events_not_set)
            messageToShow.append(buildSpannedString {
                inSpans(object : ClickableSpan() {
                    override fun onClick(textView: View) = findNavController().navigateSafe(
                        CalendarFragmentDirections.navigateToSettings(
                            INTERFACE_CALENDAR_TAB, PREF_HOLIDAY_TYPES
                        )
                    )
                }) { append(title) }
            })

            contentDescription
                .append("\n")
                .append(title)
        }

        if (messageToShow.isNotEmpty()) {
            eventsBinding.eventMessage.let {
                it.text = messageToShow
                it.movementMethod = LinkMovementMethod.getInstance()
                it.isVisible = true
            }
        }

        eventsBinding.root.contentDescription = contentDescription
    }

    private fun setOwghat(jdn: Jdn, isToday: Boolean) {
        val coordinate = coordinates ?: return
        val owghatBinding = owghatBinding ?: return

        val prayTimes = PrayTimesCalculator.calculate(
            calculationMethod, jdn.toJavaCalendar().time, coordinate
        )
        owghatBinding.timesFlow.update(prayTimes)
        owghatBinding.sunView.let { sunView ->
            sunView.isVisible = if (isToday) {
                sunView.setSunriseSunsetMoonPhase(prayTimes, runCatching {
                    SunMoonPosition(
                        jdn.value.toDouble(), coordinate.latitude, coordinate.longitude,
                        coordinate.elevation, 0.0
                    ).moonPhase
                }.onFailure(logException).getOrNull() ?: 1.0)
                true
            } else false
            if (isToday && mainBinding?.viewPager?.currentItem == OWGHAT_TAB) sunView.startAnimate()
        }
    }

    private fun setupMenu(toolbar: Toolbar, calendarPager: CalendarPager) {
        val context = toolbar.context

        val searchView = SearchView(context)
        searchView.setOnCloseListener {
            onBackPressedCloseSearchCallback.isEnabled = false
            false // don't prevent the event cascade
        }
        searchView.setOnSearchClickListener {
            onBackPressedCloseSearchCallback.isEnabled = true
            // Remove search edit view below bar
            searchView.findViewById<View?>(androidx.appcompat.R.id.search_plate)
                ?.setBackgroundColor(Color.TRANSPARENT)

            val searchAutoComplete = searchView.findViewById<SearchAutoComplete?>(
                androidx.appcompat.R.id.search_src_text
            ).debugAssertNotNull
            searchAutoComplete?.setHint(R.string.search_in_events)
            val events = allEnabledEvents + getAllEnabledAppointments(context)
            searchAutoComplete?.setAdapter(SearchEventsAdapter(context, events))
            searchAutoComplete?.setOnItemClickListener { parent, _, position, _ ->
                val date = (parent.getItemAtPosition(position) as CalendarEvent<*>).date
                val type = date.calendarType
                val today = Jdn.today.toCalendar(type)
                bringDate(
                    Jdn(
                        type, if (date.year == -1)
                            (today.year + if (date.month < today.month) 1 else 0)
                        else date.year, date.month, date.dayOfMonth
                    )
                )
                searchView.onActionViewCollapsed()
            }
        }
        this.searchView = searchView

        toolbar.menu.add(R.string.return_to_today).also {
            it.icon = toolbar.context.getCompatDrawable(R.drawable.ic_restore_modified)
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            it.isVisible = false
            it.onClick { bringDate(Jdn.today, highlight = false) }
            todayButton = it
        }
        toolbar.menu.add(R.string.search_in_events).also {
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            it.actionView = searchView
        }
        toolbar.menu.add(R.string.goto_date).also {
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            it.onClick { showDayPickerDialog(selectedJdn, R.string.go) { jdn -> bringDate(jdn) } }
        }
        toolbar.menu.add(R.string.add_event).also {
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            it.onClick { addEventOnCalendar(selectedJdn) }
        }
        toolbar.menu.add(R.string.shift_work_settings).also {
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            it.onClick { showShiftWorkDialog(selectedJdn) }
        }
        toolbar.menu.add(R.string.month_overview).also {
            it.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            it.onClick { showMonthOverviewDialog(calendarPager.selectedMonth) }
        }
    }
}
