package com.sgr.app.ui.user

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.*
import android.widget.*
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sgr.app.R
import com.sgr.app.databinding.FragmentMyRequestsBinding
import com.sgr.app.model.Reservation
import com.sgr.app.model.UpdateReservationRequest
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import com.sgr.app.utils.showConfirmDialog
import com.sgr.app.utils.showErrorDialog
import kotlinx.coroutines.launch
import java.util.Calendar

class MyRequestsFragment : Fragment() {

    private var _binding: FragmentMyRequestsBinding? = null
    private val binding get() = _binding!!
    private var currentPage = 0
    private var totalPages = 1
    private var currentStatusFilter = ""

    private val statusLabels = arrayOf("Todos los estados", "Pendiente", "Aprobada", "Rechazada", "Cancelada", "Devuelta")
    private val statusValues = arrayOf("", "PENDIENTE", "APROBADA", "RECHAZADA", "CANCELADA", "DEVUELTA")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val statusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statusLabels)
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatus.adapter = statusAdapter
        binding.spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentStatusFilter = statusValues[pos]; currentPage = 0; load()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        binding.btnReset.setOnClickListener {
            currentStatusFilter = ""; currentPage = 0
            binding.spinnerStatus.setSelection(0); load()
        }

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }

        // Banner con "Gestión de Solicitudes" en negritas
        val bannerText = "Gestión de Solicitudes: Puedes modificar o cancelar mientras estén en Pendiente. Una vez aprobadas, rechazadas o devueltas, se deshabilitan."
        val spannable = SpannableString(bannerText)
        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, "Gestión de Solicitudes:".length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvInfoBanner.text = spannable

        load()
    }

    private fun load() {
        val session = SessionManager(requireContext())
        val filter = currentStatusFilter
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getMyReservations(session.userId, currentPage, 4, filter)
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Página ${currentPage + 1} de $totalPages"
                    if (page.content.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.recyclerView.adapter = null
                    } else {
                        binding.recyclerView.adapter = MyReservationAdapter(
                            items = page.content,
                            onView = { navigateToDetail(it) },
                            onCancel = { showCancelConfirmDialog(it, session) },
                            onEdit = { showEditReservationDialog(it) },
                            onViewRejection = { showRejectionReasonDialog(it) }
                        )
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar solicitudes", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun statusLabel(status: String) = when (status) {
        "PENDIENTE" -> "Pendiente"
        "APROBADA" -> "En préstamo"
        "RECHAZADA" -> "Rechazada"
        "CANCELADA" -> "Cancelada"
        "DEVUELTA" -> "Devuelta"
        else -> status
    }

    private fun navigateToDetail(r: Reservation) {
        ReservationDetailFragment.newInstance(r)
            .show(childFragmentManager, "reservation_detail")
    }

    private fun showViewReservationDialog(r: Reservation) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reservation_detail, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(view).create()

        fun bind(res: Reservation) {
            val resourceName = res.resourceName ?: if (res.resourceType == "SPACE") res.spaceName else res.equipmentName
            val resourceType = if (res.resourceType == "SPACE") "Espacio" else "Equipo"
            val scheduleParts = res.schedule?.split(" - ")
            val startTime = res.startTime ?: scheduleParts?.getOrNull(0)
            val endTime = res.endTime ?: scheduleParts?.getOrNull(1)
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
            view.findViewById<TextView>(R.id.tvDId).text           = "${res.id}"
            view.findViewById<TextView>(R.id.tvDStatus).text       = statusLabel(res.status ?: "")
            view.findViewById<TextView>(R.id.tvDResource).text     = resourceName ?: "—"
            view.findViewById<TextView>(R.id.tvDResourceType).text = resourceType
            view.findViewById<TextView>(R.id.tvDDate).text         = res.reservationDate ?: "—"
            view.findViewById<TextView>(R.id.tvDStartTime).text    = startTime ?: "—"
            view.findViewById<TextView>(R.id.tvDReturnDate).text   = res.endDate ?: res.reservationDate ?: "—"
            view.findViewById<TextView>(R.id.tvDEndTime).text      = endTime ?: "—"
            view.findViewById<TextView>(R.id.tvDPurpose).text      = res.purpose?.ifBlank { "—" } ?: "—"

            // Comentario admin (mostrar solo si hay dato)
            val sectionAdmin = view.findViewById<LinearLayout>(R.id.sectionAdminComment)
            val adminComment = res.adminComment
            if (!adminComment.isNullOrBlank()) {
                sectionAdmin.visibility = View.VISIBLE
                view.findViewById<TextView>(R.id.tvDAdminComment).text = adminComment
            } else {
                sectionAdmin.visibility = View.GONE
            }

            // Sección devolución
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

        bind(r)

        view.findViewById<View>(R.id.btnDetailDismiss).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnDetailClose).setOnClickListener { dialog.dismiss() }

        dialog.show()

        // Cargar datos completos desde la API (purpose, startTime/endTime exactos)
        val session = SessionManager(requireContext())
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(requireContext()).getMyReservation(r.id, session.userId)
                if (response.isSuccessful) {
                    response.body()?.let { bind(it) }
                } else {
                    Toast.makeText(requireContext(), "Error ${response.code()} al cargar detalle", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRejectionReasonDialog(r: Reservation) {
        val session = SessionManager(requireContext())
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(requireContext())
                    .getMyReservation(r.id, session.userId)
                val reason = if (response.isSuccessful) {
                    response.body()?.adminComment?.takeIf { it.isNotBlank() }
                        ?: "No se proporcionó motivo de rechazo."
                } else {
                    r.adminComment?.takeIf { it.isNotBlank() } ?: "No se proporcionó motivo de rechazo."
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Motivo de rechazo")
                    .setMessage(reason)
                    .setPositiveButton("Cerrar", null).show()
            } catch (_: Exception) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Motivo de rechazo")
                    .setMessage(r.adminComment?.takeIf { it.isNotBlank() } ?: "No se proporcionó motivo de rechazo.")
                    .setPositiveButton("Cerrar", null).show()
            }
        }
    }

    private fun showCancelConfirmDialog(r: Reservation, session: SessionManager) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_cancel_confirm, null)
        val resourceName = r.resourceName ?: if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
        view.findViewById<TextView>(R.id.tvCancelQuestion).text =
            "¿Estás seguro que deseas cancelar ${resourceName ?: "esta solicitud"}?"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnKeep)
            .setOnClickListener { dialog.dismiss() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmCancel)
            .setOnClickListener {
                dialog.dismiss()
                lifecycleScope.launch {
                    try {
                        RetrofitClient.create(requireContext()).cancelMyReservation(r.id, session.userId)
                        Toast.makeText(requireContext(), "Solicitud cancelada", Toast.LENGTH_SHORT).show()
                        load()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        dialog.show()
    }

    private fun showEditReservationDialog(r: Reservation) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reservation_edit, null)
        val resourceName = r.resourceName ?: if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
        val resourceType = if (r.resourceType == "SPACE") "Espacio" else "Equipo"

        view.findViewById<TextView>(R.id.tvEditResource).text = resourceName ?: "—"
        view.findViewById<TextView>(R.id.tvEditType).text = resourceType

        val scheduleParts = r.schedule?.split(" - ")
        val etStartDate = view.findViewById<EditText>(R.id.etStartDate).apply { setText(r.reservationDate ?: "") }
        val etStartTime = view.findViewById<EditText>(R.id.etStartTime).apply { setText(r.startTime ?: scheduleParts?.getOrNull(0) ?: "") }
        val etEndDate   = view.findViewById<EditText>(R.id.etEndDate).apply { setText(r.endDate ?: r.reservationDate ?: "") }
        val etEndTime   = view.findViewById<EditText>(R.id.etEndTime).apply { setText(r.endTime ?: scheduleParts?.getOrNull(1) ?: "") }
        val etPurpose   = view.findViewById<EditText>(R.id.etPurpose).apply { setText(r.purpose ?: "") }

        // Helper: muestra DatePickerDialog y escribe YYYY-MM-DD en el EditText
        fun showDatePicker(et: EditText) {
            val cal = Calendar.getInstance()
            val parts = et.text.toString().split("-")
            if (parts.size == 3) {
                cal.set(parts[0].toIntOrNull() ?: cal.get(Calendar.YEAR),
                    (parts[1].toIntOrNull() ?: 1) - 1,
                    parts[2].toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH))
            }
            val picker = DatePickerDialog(requireContext(), { _, y, m, d ->
                et.setText("%04d-%02d-%02d".format(y, m + 1, d))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            picker.datePicker.minDate = System.currentTimeMillis()
            picker.show()
        }

        // Helper: muestra TimePickerDialog y escribe HH:MM en el EditText
        fun showTimePicker(et: EditText) {
            val parts = et.text.toString().split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(requireContext(), { _, hour, min ->
                et.setText("%02d:%02d".format(hour, min))
            }, h, m, true).show()
        }

        // Horarios permitidos: inicio 07-21, devolución 07-22
        fun showRestrictedTimePicker(et: EditText, maxHour: Int) {
            val parts = et.text.toString().split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(requireContext(), { _, hour, min ->
                if (hour < 7 || hour > maxHour || (hour == maxHour && min > 0)) {
                    Toast.makeText(requireContext(),
                        "Horario permitido: 07:00 – ${"%02d:00".format(maxHour)}",
                        Toast.LENGTH_SHORT).show()
                } else {
                    et.setText("%02d:%02d".format(hour, min))
                }
            }, h, m, true).show()
        }

        etStartDate.setOnClickListener { showDatePicker(etStartDate) }
        etStartDate.isFocusable = false
        etStartTime.setOnClickListener { showRestrictedTimePicker(etStartTime, 21) }
        etStartTime.isFocusable = false
        etEndDate.setOnClickListener { showDatePicker(etEndDate) }
        etEndDate.isFocusable = false
        etEndTime.setOnClickListener { showRestrictedTimePicker(etEndTime, 21) }
        etEndTime.isFocusable = false

        val session = SessionManager(requireContext())
        val resourceId = if (r.resourceType == "SPACE") r.spaceId ?: 0L else r.equipmentId ?: 0L

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        view.findViewById<MaterialButton>(R.id.btnEditCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<MaterialButton>(R.id.btnEditSave).setOnClickListener {
            val startDate = etStartDate.text.toString().trim()
            val startTime = etStartTime.text.toString().trim()
            val endDate   = etEndDate.text.toString().trim()
            val endTime   = etEndTime.text.toString().trim()
            val purpose   = etPurpose.text.toString().trim()

            if (startDate.isEmpty() || startTime.isEmpty() || endDate.isEmpty() || endTime.isEmpty() || purpose.isEmpty()) {
                Toast.makeText(requireContext(), "Completa todos los campos requeridos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (purpose.length < 10) {
                Toast.makeText(requireContext(), "El motivo debe tener al menos 10 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validar horarios
            val startH = startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 0
            val startM = startTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
            val endH   = endTime.split(":").getOrNull(0)?.toIntOrNull() ?: 0
            val endM   = endTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
            if (startH < 7 || startH > 21 || (startH == 21 && startM > 0)) {
                Toast.makeText(requireContext(), "Hora de inicio: 07:00 – 21:00", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (endH < 7 || endH > 21 || (endH == 21 && endM > 0)) {
                Toast.makeText(requireContext(), "Hora de devolución: 07:00 – 21:00", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validar que la fecha/hora de inicio no sea en el pasado
            try {
                val now = Calendar.getInstance()
                val startCal = Calendar.getInstance()
                val dp = startDate.split("-")
                val tp = startTime.split(":")
                startCal.set(dp[0].toInt(), dp[1].toInt() - 1, dp[2].toInt(), tp[0].toInt(), tp[1].toInt(), 0)
                startCal.set(Calendar.MILLISECOND, 0)
                if (startCal.before(now)) {
                    Toast.makeText(requireContext(), "No puedes reservar en una fecha u hora pasada", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } catch (_: Exception) { }

            showConfirmDialog(requireContext(), "¿Guardar cambios?", "Se actualizará tu solicitud. ¿Deseas continuar?") {
                lifecycleScope.launch {
                try {
                    val api = RetrofitClient.create(requireContext())

                    // Verificar disponibilidad (excluyendo la propia solicitud que se edita)
                    val historyResp = if (r.resourceType == "SPACE")
                        api.getSpaceHistory(resourceId)
                    else
                        api.getEquipmentHistory(resourceId)

                    if (historyResp.isSuccessful) {
                        val existing = historyResp.body() ?: emptyList()
                        val reqStart = "$startDate $startTime"
                        val reqEnd   = "$endDate $endTime"

                        val conflict = existing
                            .filter { it.id != r.id }
                            .filter { it.status == "PENDIENTE" || it.status == "APROBADA" }
                            .any { ex ->
                                val rStart = "${ex.reservationDate ?: ""} ${ex.startTime ?: ""}"
                                val rEnd   = "${ex.endDate ?: ex.reservationDate ?: ""} ${ex.endTime ?: ""}"
                                rStart < reqEnd && reqStart < rEnd
                            }

                        if (conflict) {
                            showErrorDialog(
                                requireContext(),
                                "Horario no disponible",
                                "Este ${if (r.resourceType == "SPACE") "espacio" else "equipo"} ya está ocupado en el horario seleccionado. Por favor elige otra fecha u horario."
                            )
                            return@launch
                        }
                    }

                    val req = UpdateReservationRequest(
                        requesterId     = session.userId,
                        resourceType    = r.resourceType,
                        resourceId      = resourceId,
                        reservationDate = startDate,
                        startTime       = startTime,
                        endDate         = endDate,
                        endTime         = endTime,
                        purpose         = purpose,
                        observations    = r.observations
                    )
                    val resp = api.updateMyReservation(r.id, session.userId, req)
                    if (resp.isSuccessful) {
                        dialog.dismiss()
                        Toast.makeText(requireContext(), "Solicitud actualizada correctamente", Toast.LENGTH_SHORT).show()
                        load()
                    } else {
                        Toast.makeText(requireContext(), "Error al guardar (${resp.code()})", Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class MyReservationAdapter(
    private val items: List<Reservation>,
    private val onView: (Reservation) -> Unit,
    private val onCancel: (Reservation) -> Unit,
    private val onEdit: (Reservation) -> Unit,
    private val onViewRejection: (Reservation) -> Unit
) : RecyclerView.Adapter<MyReservationAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_my_reservation, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.view.apply {
            val resourceName = r.resourceName ?: if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
            val resourceType = if (r.resourceType == "SPACE") "Espacio" else "Equipo"

findViewById<TextView>(R.id.tvResourceName).text = resourceName ?: "—"
            findViewById<TextView>(R.id.tvType).text = resourceType
            findViewById<TextView>(R.id.tvDate).text = r.reservationDate ?: "—"
            findViewById<TextView>(R.id.tvEndDate).text = r.endDate ?: "—"
            findViewById<TextView>(R.id.tvSchedule).text = r.schedule ?: "${r.startTime ?: "—"} - ${r.endTime ?: "—"}"

            // Badge estado
            val tvStatus = findViewById<TextView>(R.id.tvStatus)
            tvStatus.text = when (r.status ?: "") {
                "PENDIENTE" -> "Pendiente"
                "APROBADA" -> "En préstamo"
                "RECHAZADA" -> "Rechazada"
                "CANCELADA" -> "Cancelada"
                "DEVUELTA" -> "Devuelta"
                else -> r.status
            }
            when (r.status ?: "") {
                "PENDIENTE" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_yellow); tvStatus.setTextColor(0xFF92400E.toInt()) }
                "APROBADA" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_green); tvStatus.setTextColor(0xFF065F46.toInt()) }
                "RECHAZADA" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_red); tvStatus.setTextColor(0xFF991B1B.toInt()) }
                "DEVUELTA" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_blue); tvStatus.setTextColor(0xFF1D4ED8.toInt()) }
                else -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_gray); tvStatus.setTextColor(0xFF6B7280.toInt()) }
            }

            // Botones
            val btnView = findViewById<ImageButton>(R.id.btnView)
            val btnEdit = findViewById<ImageButton>(R.id.btnEdit)
            val btnCancel = findViewById<ImageButton>(R.id.btnCancel)
            val btnRejection = findViewById<ImageButton>(R.id.btnRejection)

            btnView.setOnClickListener { onView(r) }

            if (r.status == "PENDIENTE") {
                btnEdit.alpha = 1f; btnEdit.isEnabled = true
                btnCancel.alpha = 1f; btnCancel.isEnabled = true
                btnEdit.setOnClickListener { onEdit(r) }
                btnCancel.setOnClickListener { onCancel(r) }
                btnRejection.visibility = View.GONE
            } else if (r.status == "RECHAZADA") {
                btnEdit.alpha = 0.3f; btnEdit.isEnabled = false
                btnCancel.alpha = 0.3f; btnCancel.isEnabled = false
                btnRejection.visibility = View.VISIBLE
                btnRejection.setOnClickListener { onViewRejection(r) }
            } else {
                btnEdit.alpha = 0.3f; btnEdit.isEnabled = false
                btnCancel.alpha = 0.3f; btnCancel.isEnabled = false
                btnRejection.visibility = View.GONE
            }
        }
    }
}
