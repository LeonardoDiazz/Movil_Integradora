package com.sgr.app.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentReservationsBinding
import com.sgr.app.model.ApproveRequest
import com.sgr.app.model.CreateReservationRequest
import com.sgr.app.model.Reservation
import com.sgr.app.model.RejectRequest
import com.sgr.app.model.ReturnRequest
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReservationsFragment : Fragment() {

    private var _binding: FragmentReservationsBinding? = null
    private val binding get() = _binding!!
    private var currentPage = 0
    private var totalPages = 1

    private val estatusLabels = arrayOf("Todos los Estatus", "Pendiente", "Aprobada (En préstamo)", "Rechazada", "Devuelta", "Cancelada")
    private val estatusValues = arrayOf("", "PENDIENTE", "APROBADA", "RECHAZADA", "DEVUELTA", "CANCELADA")

    private val tipoLabels = arrayOf("Todos los Tipos", "Espacio", "Equipo")
    private val tipoValues = arrayOf("", "SPACE", "EQUIPMENT")

    private var filterEstatus = ""
    private var filterTipo = ""
    private var requesterSearch = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReservationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerFilter, estatusLabels) { pos ->
            if (!isInitializing) { filterEstatus = estatusValues[pos]; filterTipo = ""; currentPage = 0; load() }
        }
        setupSpinner(binding.spinnerTipo, tipoLabels) { pos ->
            if (!isInitializing) { filterTipo = tipoValues[pos]; filterEstatus = ""; currentPage = 0; load() }
        }

        isInitializing = false

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { requesterSearch = s?.toString()?.trim() ?: ""; currentPage = 0; load() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnClearFilters.setOnClickListener {
            filterEstatus = ""; filterTipo = ""; requesterSearch = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerFilter.setSelection(0)
            binding.spinnerTipo.setSelection(0)
            isInitializing = false
            binding.etSearch.text?.clear()
            load()
        }

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }
        binding.btnAddReservation.setOnClickListener { showCreateReservationDialog() }

        load()
    }

    private fun showCreateReservationDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 16, 48, 0) }

        fun label(text: String) = TextView(ctx).apply { this.text = text; textSize = 12f; setPadding(0, 12, 0, 2) }

        val etRequesterId = EditText(ctx).apply { hint = "ID del solicitante (número)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etDate = EditText(ctx).apply { hint = "YYYY-MM-DD" }
        val etStart = EditText(ctx).apply { hint = "HH:MM" }
        val etEnd = EditText(ctx).apply { hint = "HH:MM" }
        val etPurpose = EditText(ctx).apply { hint = "Motivo de la reserva"; minLines = 3 }
        val etObs = EditText(ctx).apply { hint = "Observaciones (opcional)" }

        val typeValues = arrayOf("SPACE", "EQUIPMENT")
        val typeLabels = arrayOf("Espacio", "Equipo")
        val spinnerType = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, typeLabels)
        }
        val etResourceId = EditText(ctx).apply { hint = "ID del recurso (número)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }

        layout.addView(label("ID Solicitante *")); layout.addView(etRequesterId)
        layout.addView(label("Tipo de recurso")); layout.addView(spinnerType)
        layout.addView(label("ID del recurso *")); layout.addView(etResourceId)
        layout.addView(label("Fecha *")); layout.addView(etDate)
        layout.addView(label("Hora inicio *")); layout.addView(etStart)
        layout.addView(label("Hora fin *")); layout.addView(etEnd)
        layout.addView(label("Motivo *")); layout.addView(etPurpose)
        layout.addView(label("Observaciones")); layout.addView(etObs)

        val sv = ScrollView(ctx).apply { addView(layout) }

        AlertDialog.Builder(ctx)
            .setTitle("Crear reservación")
            .setView(sv)
            .setPositiveButton("Crear") { _, _ ->
                val requesterId = etRequesterId.text.toString().toLongOrNull()
                val resourceId = etResourceId.text.toString().toLongOrNull()
                val date = etDate.text.toString().trim()
                val start = etStart.text.toString().trim()
                val end = etEnd.text.toString().trim()
                val purpose = etPurpose.text.toString().trim()

                if (requesterId == null || resourceId == null || date.isEmpty() || start.isEmpty() || end.isEmpty() || purpose.isEmpty()) {
                    Toast.makeText(ctx, "Completa todos los campos requeridos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val selectedType = typeValues[spinnerType.selectedItemPosition]
                lifecycleScope.launch {
                    try {
                        val api = RetrofitClient.create(ctx)
                        val req = CreateReservationRequest(
                            requesterId = requesterId,
                            resourceType = selectedType,
                            resourceId = resourceId,
                            reservationDate = date,
                            startTime = start,
                            endDate = null,
                            endTime = end,
                            purpose = purpose,
                            observations = etObs.text.toString().trim().ifEmpty { null }
                        )
                        val resp = api.createReservation(req)
                        if (resp.isSuccessful) {
                            Toast.makeText(ctx, "Reservación creada", Toast.LENGTH_SHORT).show(); load()
                        } else Toast.makeText(ctx, "Error: ${resp.code()}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) { Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun navigateToDetail(r: Reservation) {
        AdminReservationDetailFragment.newInstance(r)
            .show(childFragmentManager, "reservation_detail")
    }

    private fun showViewReservationDialog(r: Reservation) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reservation_detail, null)

        fun fillDialog(res: Reservation) {
            val resourceName = res.resourceName
                ?: if (res.resourceType == "SPACE") res.spaceName ?: "—" else res.equipmentName ?: "—"
            val resourceTypeLabel = if (res.resourceType == "SPACE") "Espacio" else "Equipo"
            val statusLabel = when (res.status) {
                "PENDIENTE" -> "Pendiente"
                "APROBADA"  -> "Aprobada"
                "RECHAZADA" -> "Rechazada"
                "DEVUELTA"  -> "Devuelta"
                "CANCELADA" -> "Cancelada"
                else        -> res.status ?: "—"
            }
            val requesterTypeLabel = when (res.requesterType) {
                "ESTUDIANTE" -> "Estudiante"
                "STAFF"      -> "Personal"
                else         -> res.requesterType ?: "—"
            }

            // Solicitante
            view.findViewById<TextView>(R.id.tvDRequester).text     = res.requesterName ?: "—"
            view.findViewById<TextView>(R.id.tvDEmail).text         = res.requesterEmail ?: "—"
            view.findViewById<TextView>(R.id.tvDRequesterType).text = requesterTypeLabel

            // Reserva
            view.findViewById<TextView>(R.id.tvDResourceType).text  = resourceTypeLabel
            view.findViewById<TextView>(R.id.tvDResource).text      = resourceName
            view.findViewById<TextView>(R.id.tvDDate).text          = res.reservationDate ?: "—"
            view.findViewById<TextView>(R.id.tvDStartTime).text     = res.startTime ?: "—"
            view.findViewById<TextView>(R.id.tvDReturnDate).text    = res.endDate ?: res.reservationDate ?: "—"
            view.findViewById<TextView>(R.id.tvDEndTime).text       = res.endTime ?: "—"
            view.findViewById<TextView>(R.id.tvDStatus).text        = statusLabel

            // Notas
            view.findViewById<TextView>(R.id.tvDPurpose).text      = res.purpose?.ifBlank { "—" } ?: "—"
            view.findViewById<TextView>(R.id.tvDObservations).text = res.observations?.ifBlank { "—" } ?: "—"

            // Comentario del admin — mostrar sección solo si hay dato
            val adminComment = res.adminComment
            val sectionAdmin = view.findViewById<LinearLayout>(R.id.sectionAdminComment)
            if (!adminComment.isNullOrBlank()) {
                sectionAdmin.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.tvDAdminComment).text = adminComment
            } else {
                sectionAdmin.visibility = View.GONE
            }

            // Sección devolución — mostrar solo si fue devuelta
            val sectionReturn = view.findViewById<LinearLayout>(R.id.sectionReturn)
            if (!res.returnCondition.isNullOrBlank() || !res.returnedAt.isNullOrBlank()) {
                sectionReturn.visibility = View.VISIBLE
                val condLabel = when (res.returnCondition) {
                    "BUEN_ESTADO" -> "Buen estado"
                    "DAÑADO"      -> "Dañado"
                    else          -> res.returnCondition ?: "—"
                }
                view.findViewById<TextView>(R.id.tvDReturnCondition).text   = condLabel
                view.findViewById<TextView>(R.id.tvDReturnedAt).text        = res.returnedAt ?: "—"
                view.findViewById<TextView>(R.id.tvDReturnDescription).text = res.returnDescription?.ifBlank { "—" } ?: "—"
            } else {
                sectionReturn.visibility = View.GONE
            }
        }

        // Mostrar datos del listado de inmediato (parciales)
        fillDialog(r)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnDetailClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDetailDismiss)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()

        // Cargar datos completos desde el endpoint de detalle
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(requireContext()).getReservation(r.id)
                if (response.isSuccessful && dialog.isShowing) {
                    response.body()?.let { fillDialog(it) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun setupSpinner(spinner: Spinner, labels: Array<String>, onSelect: (Int) -> Unit) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { onSelect(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun load() {
        val backendFilter = when {
            filterEstatus.isNotEmpty() -> filterEstatus
            filterTipo.isNotEmpty() -> filterTipo
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getReservations(currentPage, 4, backendFilter)
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    val filtered = if (requesterSearch.isEmpty()) page.content
                        else page.content.filter { (it.requesterName ?: "").contains(requesterSearch, ignoreCase = true) }
                    binding.recyclerView.adapter = ReservationAdapter(filtered) { action, reservation ->
                        when (action) {
                            "approve" -> showApproveDialog(reservation)
                            "reject"  -> showRejectDialog(reservation)
                            "return"  -> showReturnDialog(reservation)
                            "view"    -> navigateToDetail(reservation)
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar reservaciones", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showApproveDialog(r: Reservation) {
        val resourceName = r.resourceName
            ?: if (r.resourceType == "SPACE") r.spaceName ?: "—" else r.equipmentName ?: "—"
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_approve_confirm, null)
        view.findViewById<TextView>(R.id.tvApproveMessage).text =
            "¿Estás seguro que deseas aprobar la solicitud de ${r.requesterName ?: "este usuario"} para $resourceName?"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApproveCancelConfirm)
            .setOnClickListener { dialog.dismiss() }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnApproveConfirm)
            .setOnClickListener {
                dialog.dismiss()
                lifecycleScope.launch {
                    try {
                        val api = RetrofitClient.create(requireContext())
                        val resourceId = r.spaceId ?: r.equipmentId ?: 0L

                        if (resourceId == 0L) {
                            showNotAvailableDialog(if (r.resourceType == "SPACE") "espacio" else "equipo")
                            return@launch
                        }

                        val historyResp = if (r.resourceType == "SPACE")
                            api.getSpaceHistory(resourceId)
                        else
                            api.getEquipmentHistory(resourceId)

                        if (historyResp.isSuccessful) {
                            val existing = historyResp.body() ?: emptyList()

                            fun resolveTime(time: String?, schedule: String?, idx: Int) =
                                time?.takeIf { it.isNotBlank() }
                                    ?: schedule?.split(" - ")?.getOrNull(idx)?.trim() ?: ""

                            val reqStartTime = resolveTime(r.startTime, r.schedule, 0)
                            val reqEndTime   = resolveTime(r.endTime,   r.schedule, 1)
                            val reqStart = "${r.reservationDate ?: ""} $reqStartTime"
                            val reqEnd   = "${r.endDate ?: r.reservationDate ?: ""} $reqEndTime"

                            val conflict = existing
                                .filter { it.id != r.id && it.status == "APROBADA" }
                                .any { ex ->
                                    val exStart = "${ex.reservationDate ?: ""} ${resolveTime(ex.startTime, ex.schedule, 0)}"
                                    val exEnd   = "${ex.endDate ?: ex.reservationDate ?: ""} ${resolveTime(ex.endTime, ex.schedule, 1)}"
                                    exStart < reqEnd && reqStart < exEnd
                                }

                            if (conflict) {
                                showNotAvailableDialog(if (r.resourceType == "SPACE") "espacio" else "equipo")
                                return@launch
                            }
                        }

                        api.approveReservation(r.id, ApproveRequest(null))
                        Toast.makeText(requireContext(), "Reservación aprobada", Toast.LENGTH_SHORT).show()
                        load()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        dialog.show()
    }

    private fun showNotAvailableDialog(resourceLabel: String) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_not_available, null)
        view.findViewById<TextView>(R.id.tvNotAvailableMessage).text =
            "Error: este $resourceLabel no está disponible en el horario solicitado."
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.btnNotAvailableOk).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showRejectDialog(r: Reservation) {
        val input = EditText(requireContext()).apply { hint = "Motivo de rechazo (requerido)" }
        AlertDialog.Builder(requireContext())
            .setTitle("Rechazar reservación")
            .setView(input)
            .setPositiveButton("Rechazar") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.length < 10) {
                    Toast.makeText(requireContext(), "El motivo debe tener al menos 10 caracteres", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    try {
                        RetrofitClient.create(requireContext()).rejectReservation(r.id, RejectRequest(reason))
                        Toast.makeText(requireContext(), "Reservación rechazada", Toast.LENGTH_SHORT).show()
                        load()
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showReturnDialog(r: Reservation) {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_return_form, null)

        // Info
        view.findViewById<TextView>(R.id.tvReturnRequester).text = r.requesterName ?: "—"
        val resourceName = r.resourceName
            ?: if (r.resourceType == "SPACE") r.spaceName ?: "—" else r.equipmentName ?: "—"
        view.findViewById<TextView>(R.id.tvReturnResource).text = resourceName

        // Fechas
        val plannedDate = r.endDate ?: r.reservationDate ?: "—"
        view.findViewById<TextView>(R.id.tvPlannedDate).text = plannedDate
        val nowFormatted = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())
        view.findViewById<TextView>(R.id.tvRealReturnDate).text = nowFormatted

        // Opciones de condición
        val optBuenEstado   = view.findViewById<LinearLayout>(R.id.optBuenEstado)
        val optDanado       = view.findViewById<LinearLayout>(R.id.optDanado)
        val tvBuenEstado    = view.findViewById<TextView>(R.id.tvBuenEstado)
        val tvDanado        = view.findViewById<TextView>(R.id.tvDanado)
        val ivBuenEstado    = view.findViewById<TextView>(R.id.ivBuenEstado)
        val sectionDamage   = view.findViewById<LinearLayout>(R.id.sectionDamageDesc)
        val etDamageDesc    = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDamageDesc)
        val tvCondError     = view.findViewById<TextView>(R.id.tvReturnConditionError)
        val tvDescError     = view.findViewById<TextView>(R.id.tvDamageDescError)

        var selectedCondition = ""

        fun selectCondition(condition: String) {
            selectedCondition = condition
            tvCondError.visibility = View.GONE
            if (condition == "BUEN_ESTADO") {
                optBuenEstado.setBackgroundResource(R.drawable.bg_card_selected)
                tvBuenEstado.setTextColor(0xFF15803D.toInt())
                ivBuenEstado.setTextColor(0xFF15803D.toInt())
                optDanado.setBackgroundResource(R.drawable.bg_card_selectable)
                tvDanado.setTextColor(0xFF334155.toInt())
                sectionDamage.visibility = View.GONE
            } else {
                optDanado.setBackgroundResource(R.drawable.bg_card_selected)
                tvDanado.setTextColor(0xFF15803D.toInt())
                optBuenEstado.setBackgroundResource(R.drawable.bg_card_selectable)
                tvBuenEstado.setTextColor(0xFF334155.toInt())
                ivBuenEstado.setTextColor(0xFF9CA3AF.toInt())
                sectionDamage.visibility = View.VISIBLE
            }
        }

        optBuenEstado.setOnClickListener { selectCondition("BUEN_ESTADO") }
        optDanado.setOnClickListener     { selectCondition("DAÑADO") }

        val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.btnReturnClose).setOnClickListener  { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnReturnCancel).setOnClickListener { dialog.dismiss() }

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReturnConfirm)
            .setOnClickListener {
                // Validaciones
                if (selectedCondition.isEmpty()) {
                    tvCondError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (selectedCondition == "DAÑADO") {
                    val desc = etDamageDesc.text.toString().trim()
                    if (desc.length < 10) {
                        tvDescError.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                }
                tvDescError.visibility = View.GONE

                lifecycleScope.launch {
                    try {
                        val desc = if (selectedCondition == "DAÑADO")
                            etDamageDesc.text.toString().trim() else null
                        RetrofitClient.create(ctx).returnReservation(
                            r.id, ReturnRequest(selectedCondition, desc)
                        )
                        Toast.makeText(ctx, "Devolución registrada", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        load()
                    } catch (_: Exception) {
                        Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        dialog.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ReservationAdapter(
    private val items: List<Reservation>,
    private val onAction: (String, Reservation) -> Unit
) : RecyclerView.Adapter<ReservationAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_reservation, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.view.apply {
            // Solicitante + recurso
            findViewById<TextView>(R.id.tvRequester).text = r.requesterName ?: r.requesterEmail ?: "—"
            val resourceName = r.resourceName ?: if (r.resourceType == "SPACE") r.spaceName ?: "—" else r.equipmentName ?: "—"
            findViewById<TextView>(R.id.tvResourceName).text = resourceName
            findViewById<TextView>(R.id.tvDate).text = r.reservationDate ?: "—"
            val timeText = r.schedule ?: "${r.startTime ?: ""}  –  ${r.endTime ?: ""}".trim()
            findViewById<TextView>(R.id.tvTime).text = timeText

            // Badge categoría
            val tvCategory = findViewById<TextView>(R.id.tvCategory)
            if (r.resourceType == "SPACE") {
                tvCategory.text = "ESPACIO"
                tvCategory.setBackgroundResource(R.drawable.bg_badge_purple)
                tvCategory.setTextColor(0xFF5B21B6.toInt())
            } else {
                tvCategory.text = "EQUIPO"
                tvCategory.setBackgroundResource(R.drawable.bg_badge_blue)
                tvCategory.setTextColor(0xFF1D4ED8.toInt())
            }

            // Badge estatus
            val tvStatus = findViewById<TextView>(R.id.tvStatus)
            when (r.status) {
                "PENDIENTE" -> {
                    tvStatus.text = "Pendiente"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_yellow)
                    tvStatus.setTextColor(0xFF92400E.toInt())
                }
                "APROBADA" -> {
                    tvStatus.text = "Aprobada"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_green)
                    tvStatus.setTextColor(0xFF065F46.toInt())
                }
                "RECHAZADA" -> {
                    tvStatus.text = "Rechazada"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_red)
                    tvStatus.setTextColor(0xFF991B1B.toInt())
                }
                "DEVUELTA" -> {
                    tvStatus.text = "Devuelta"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_blue)
                    tvStatus.setTextColor(0xFF1D4ED8.toInt())
                }
                else -> {
                    tvStatus.text = "Cancelada"
                    tvStatus.setBackgroundResource(R.drawable.bg_badge_gray)
                    tvStatus.setTextColor(0xFF374151.toInt())
                }
            }

            // Botones de acción
            val btnApprove = findViewById<ImageButton>(R.id.btnApprove)
            val btnReject = findViewById<ImageButton>(R.id.btnReject)
            val btnReturn = findViewById<ImageButton>(R.id.btnReturn)

            btnApprove.visibility = View.GONE
            btnReject.visibility = View.GONE
            btnReturn.visibility = View.GONE

            when (r.status ?: "") {
                "PENDIENTE" -> {
                    btnApprove.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                    btnApprove.setOnClickListener { onAction("approve", r) }
                    btnReject.setOnClickListener { onAction("reject", r) }
                }
                "APROBADA" -> {
                    btnReturn.visibility = View.VISIBLE
                    btnReturn.setOnClickListener { onAction("return", r) }
                }
            }

            findViewById<ImageButton>(R.id.btnViewDetail).setOnClickListener { onAction("view", r) }
            findViewById<ImageButton>(R.id.btnEditReservation).visibility = View.GONE
            findViewById<ImageButton>(R.id.btnViewRejection).visibility = View.GONE
        }
    }
}
