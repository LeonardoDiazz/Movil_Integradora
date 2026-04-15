package com.sgr.app.ui.user

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentNewRequestBinding
import com.sgr.app.model.CreateReservationRequest
import com.sgr.app.model.Equipment
import com.sgr.app.model.Space
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import com.sgr.app.utils.showErrorDialog
import kotlinx.coroutines.launch
import java.util.Calendar

data class ResourceItem(
    val id: Long,
    val name: String,
    val resourceType: String,
    val meta: String,
    val icon: String
)

class NewRequestFragment : Fragment() {

    private var _binding: FragmentNewRequestBinding? = null
    private val binding get() = _binding!!

    private var currentTab = "SPACE"
    private var currentPage = 0
    private var totalPages = 1
    private var selectedResource: ResourceItem? = null

    private var spaces = listOf<Space>()
    private var equipments = listOf<Equipment>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""

        val session = SessionManager(requireContext())
        val isStudent = session.userRole == "STUDENT" || session.userRole == "STUDENTS"

        if (isStudent) binding.tvStudentNotice.visibility = View.VISIBLE

        val noteText = "Nota importante: Todas las solicitudes se registran en estado Pendiente y no podrán ser editadas una vez aprobadas o rechazadas."
        val spannable = SpannableString(noteText)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, "Nota importante:".length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvImportantNote.text = spannable

        binding.recyclerResources.layoutManager = LinearLayoutManager(requireContext())

        // Tabs
        binding.btnTabSpace.setOnClickListener { if (currentTab != "SPACE") switchTab("SPACE") }
        binding.btnTabEquipment.setOnClickListener { if (currentTab != "EQUIPMENT") switchTab("EQUIPMENT") }

        // Search
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { currentPage = 0; loadResources() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // Pagination
        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; loadResources() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; loadResources() } }

        // Continue
        binding.btnContinue.setOnClickListener { goToStep2() }

        // Back
        binding.btnBack.setOnClickListener { goToStep1() }

        // Change resource
        binding.btnChange.setOnClickListener { goToStep1() }

        // Submit
        binding.btnSubmit.setOnClickListener { submit() }

        // Date / Time pickers
        binding.etDate.setOnClickListener { showDatePicker { binding.etDate.setText(it) } }
        binding.etEndDate.setOnClickListener { showDatePicker { binding.etEndDate.setText(it) } }
        binding.etStartTime.setOnClickListener { showTimePicker({ binding.etStartTime.setText(it) }, maxHour = 21) }
        binding.etEndTime.setOnClickListener { showTimePicker({ binding.etEndTime.setText(it) }, maxHour = 21) }

        loadResources()
    }

    private fun showDatePicker(onSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val picker = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                onSelected(String.format("%04d-%02d-%02d", year, month + 1, day))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.minDate = System.currentTimeMillis()
        picker.show()
    }

    private fun showTimePicker(onSelected: (String) -> Unit, maxHour: Int = 22) {
        val cal = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                if (hour < 7 || hour > maxHour || (hour == maxHour && minute > 0)) {
                    Toast.makeText(requireContext(),
                        "Horario permitido: 07:00 – ${"%02d:00".format(maxHour)}",
                        Toast.LENGTH_SHORT).show()
                } else {
                    onSelected(String.format("%02d:%02d", hour, minute))
                }
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        currentPage = 0
        selectedResource = null
        binding.btnContinue.isEnabled = false

        val green = android.content.res.ColorStateList.valueOf(0xFF00843D.toInt())
        val gray = android.content.res.ColorStateList.valueOf(0xFFF1F5F9.toInt())
        if (tab == "SPACE") {
            binding.btnTabSpace.backgroundTintList = green
            binding.btnTabSpace.setTextColor(0xFFFFFFFF.toInt())
            binding.btnTabEquipment.backgroundTintList = gray
            binding.btnTabEquipment.setTextColor(0xFF6B7280.toInt())
            binding.etSearch.setText("")
            binding.etSearch.hint = "Buscar espacio..."
        } else {
            binding.btnTabEquipment.backgroundTintList = green
            binding.btnTabEquipment.setTextColor(0xFFFFFFFF.toInt())
            binding.btnTabSpace.backgroundTintList = gray
            binding.btnTabSpace.setTextColor(0xFF6B7280.toInt())
            binding.etSearch.setText("")
            binding.etSearch.hint = "Buscar equipo..."
        }

        loadResources()
    }

    private fun loadResources() {
        val search = binding.etSearch.text?.toString()?.trim() ?: ""
        val session = SessionManager(requireContext())
        val isStudent = session.userRole == "STUDENT"

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val items: List<ResourceItem>

                if (currentTab == "SPACE") {
                    val resp = api.getSpaces(currentPage, 10, "", search)
                    if (!resp.isSuccessful) {
                        Toast.makeText(requireContext(), "Error al cargar espacios: ${resp.code()}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val page = resp.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    spaces = page.content
                    val filtered = page.content
                        .filter { it.active != false }
                        .filter { it.availability?.uppercase() == "DISPONIBLE" }
                        .filter { !isStudent || it.allowStudents != false }
                    items = filtered.map {
                        ResourceItem(it.id, it.name, "SPACE", "📍 ${it.location} • 👥 ${it.capacity}", "🏢")
                    }
                } else {
                    val resp = api.getEquipments(currentPage, 10, "", search)
                    if (!resp.isSuccessful) {
                        Toast.makeText(requireContext(), "Error al cargar equipos: ${resp.code()}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val page = resp.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    equipments = page.content
                    val filtered = page.content
                        .filter { it.active != false }
                        .filter { it.equipmentCondition?.uppercase() == "DISPONIBLE" }
                        .filter { !isStudent || it.allowStudents != false }
                    items = filtered.map {
                        ResourceItem(it.id, it.name, "EQUIPMENT", "${it.category} • ${it.inventoryNumber}", "📦")
                    }
                }

                binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"

                if (items.isEmpty()) {
                    binding.recyclerResources.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerResources.visibility = View.VISIBLE
                    binding.recyclerResources.adapter = ResourceAdapter(items, selectedResource?.id) { res ->
                        selectedResource = res
                        binding.btnContinue.isEnabled = true
                        (binding.recyclerResources.adapter as ResourceAdapter).setSelected(res.id)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToStep2() {
        val res = selectedResource ?: return
        binding.layoutStep1.visibility = View.GONE
        binding.layoutStep2.visibility = View.VISIBLE
        binding.step2Indicator.setBackgroundResource(R.drawable.bg_step_active)

        binding.tvBannerIcon.text = res.icon
        binding.tvBannerTitle.text = res.name
        binding.tvBannerSub.text = "${res.meta} • ${if (res.resourceType == "SPACE") "Espacio" else "Equipo"}"

    }

    private fun goToStep1() {
        binding.layoutStep2.visibility = View.GONE
        binding.layoutStep1.visibility = View.VISIBLE
        binding.step2Indicator.setBackgroundResource(R.drawable.bg_step_inactive)
    }

    private fun submit() {
        val startDate = binding.etDate.text?.toString()?.trim() ?: ""
        val startTime = binding.etStartTime.text?.toString()?.trim() ?: ""
        val endDate = binding.etEndDate.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val endTime = binding.etEndTime.text?.toString()?.trim() ?: ""
        val purpose = binding.etPurpose.text?.toString()?.trim() ?: ""
        val res = selectedResource ?: return

        // Validación por campo
        var hasError = false
        binding.tilDate.error = if (startDate.isEmpty()) { hasError = true; "La fecha de inicio es obligatoria" } else null
        binding.tilStartTime.error = if (startTime.isEmpty()) { hasError = true; "Obligatorio" } else null
        binding.tilEndDate.error = if (endDate.isNullOrEmpty()) { hasError = true; "La fecha de devolución es obligatoria" } else null
        binding.tilEndTime.error = if (endTime.isEmpty()) { hasError = true; "Obligatorio" } else null
        binding.tilPurpose.error = when {
            purpose.isEmpty() -> { hasError = true; "El motivo es obligatorio" }
            purpose.length < 10 -> { hasError = true; "Mínimo 10 caracteres" }
            else -> null
        }
        if (hasError) return

        // Validar horarios permitidos
        val startH = startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 0
        val startM = startTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
        val endH   = endTime.split(":").getOrNull(0)?.toIntOrNull() ?: 0
        val endM   = endTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
        if (startH < 7 || startH > 21 || (startH == 21 && startM > 0)) {
            binding.tilStartTime.error = "Horario: 07:00 – 21:00"; return
        } else { binding.tilStartTime.error = null }
        if (endH < 7 || endH > 21 || (endH == 21 && endM > 0)) {
            binding.tilEndTime.error = "Horario: 07:00 – 21:00"; return
        } else { binding.tilEndTime.error = null }

        // Validar que la fecha/hora de inicio no sea en el pasado
        try {
            val now = Calendar.getInstance()
            val startCal = Calendar.getInstance()
            val dateParts = startDate.split("-")
            val timeParts = startTime.split(":")
            startCal.set(
                dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt(),
                timeParts[0].toInt(), timeParts[1].toInt(), 0
            )
            startCal.set(Calendar.MILLISECOND, 0)
            if (startCal.before(now)) {
                binding.tilDate.error = "No puedes reservar en una fecha u hora pasada"
                return
            } else { binding.tilDate.error = null }
        } catch (_: Exception) { }

        val session = SessionManager(requireContext())
        binding.btnSubmit.isEnabled = false

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())

                // Verificar disponibilidad antes de crear la solicitud
                val historyResp = if (res.resourceType == "SPACE")
                    api.getSpaceHistory(res.id)
                else
                    api.getEquipmentHistory(res.id)

                if (historyResp.isSuccessful) {
                    val existing = historyResp.body() ?: emptyList()
                    val reqStart = "$startDate $startTime"
                    val reqEnd   = "${endDate ?: startDate} $endTime"

                    val conflict = existing
                        .filter { it.status == "PENDIENTE" || it.status == "APROBADA" }
                        .any { r ->
                            val rStart = "${r.reservationDate ?: ""} ${r.startTime ?: ""}"
                            val rEnd   = "${r.endDate ?: r.reservationDate ?: ""} ${r.endTime ?: ""}"
                            rStart < reqEnd && reqStart < rEnd
                        }

                    if (conflict) {
                        showErrorDialog(
                            requireContext(),
                            "Horario no disponible",
                            "Este ${if (res.resourceType == "SPACE") "espacio" else "equipo"} ya está ocupado en el horario seleccionado. Por favor elige otra fecha u horario."
                        )
                        binding.btnSubmit.isEnabled = true
                        return@launch
                    }
                }

                val response = api.createReservation(
                    CreateReservationRequest(
                        requesterId = session.userId,
                        resourceType = res.resourceType,
                        resourceId = res.id,
                        reservationDate = startDate,
                        startTime = startTime,
                        endDate = endDate,
                        endTime = endTime,
                        purpose = purpose,
                        observations = null
                    )
                )
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "¡Solicitud enviada exitosamente!", Toast.LENGTH_LONG).show()
                    selectedResource = null
                    binding.btnContinue.isEnabled = false
                    binding.etDate.text?.clear()
                    binding.etEndDate.text?.clear()
                    binding.etStartTime.text?.clear()
                    binding.etEndTime.text?.clear()
                    binding.etPurpose.text?.clear()
                    goToStep1()
                    loadResources()
                } else {
                    Toast.makeText(requireContext(), "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSubmit.isEnabled = true
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ResourceAdapter(
    private val items: List<ResourceItem>,
    private var selectedId: Long?,
    private val onSelect: (ResourceItem) -> Unit
) : RecyclerView.Adapter<ResourceAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    fun setSelected(id: Long) { selectedId = id; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_resource_card, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val isSelected = item.id == selectedId
        holder.view.apply {
            setBackgroundResource(if (isSelected) R.drawable.bg_card_selected else R.drawable.bg_card_selectable)
            findViewById<TextView>(R.id.tvResourceIcon).text = item.icon
            findViewById<TextView>(R.id.tvResourceName).text = item.name
            findViewById<TextView>(R.id.tvResourceLocation).text = item.meta
            findViewById<TextView>(R.id.tvResourceMeta).text = item.meta
            val check = findViewById<TextView>(R.id.tvCheckMark)
            check.visibility = if (isSelected) View.VISIBLE else View.GONE
            setOnClickListener { onSelect(item) }
        }
    }
}

