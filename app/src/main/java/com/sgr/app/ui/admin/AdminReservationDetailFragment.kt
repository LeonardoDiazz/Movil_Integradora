package com.sgr.app.ui.admin

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.sgr.app.R
import com.sgr.app.model.Reservation
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AdminReservationDetailFragment : DialogFragment() {

    companion object {
        private const val ARG_ID = "reservation_id"

        fun newInstance(reservation: Reservation): AdminReservationDetailFragment {
            return AdminReservationDetailFragment().apply {
                arguments = Bundle().apply { putLong(ARG_ID, reservation.id) }
            }
        }
    }

    private var reservationId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ReservationDetailDialog)
        reservationId = arguments?.getLong(ARG_ID) ?: 0L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_reservation_detail, container, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also {
            it.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dismissClick = View.OnClickListener { dismiss() }
        view.findViewById<TextView>(R.id.btnClose).setOnClickListener(dismissClick)
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCerrar)
            .setOnClickListener(dismissClick)
        loadDetail(view)
    }

    private fun loadDetail(view: View) {
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(requireContext()).getReservation(reservationId)
                if (response.isSuccessful) {
                    response.body()?.let { bind(view, it) }
                } else {
                    Toast.makeText(requireContext(), "Error ${response.code()} al cargar detalle", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error de conexión: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun formatDateTime(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return try {
            val dt = LocalDateTime.parse(raw)
            dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        } catch (e: Exception) {
            raw.take(16).replace("T", " ")
        }
    }

    private fun bind(view: View, r: Reservation) {
        // Solicitante
        view.findViewById<TextView>(R.id.tvRequesterName).text = r.requesterName ?: "—"
        view.findViewById<TextView>(R.id.tvRequesterEmail).text = r.requesterEmail ?: "—"
        view.findViewById<TextView>(R.id.tvRequesterType).text = when (r.requesterType) {
            "ESTUDIANTE" -> "Estudiante"
            "STAFF"      -> "Personal"
            else         -> r.requesterType ?: "—"
        }

        // Recurso
        val resourceName = r.resourceName ?: if (r.resourceType == "SPACE") r.spaceName else r.equipmentName
        view.findViewById<TextView>(R.id.tvResourceType).text =
            if (r.resourceType == "SPACE") "Espacio" else "Equipo"
        view.findViewById<TextView>(R.id.tvResourceName).text = resourceName ?: "—"

        // Horario
        val scheduleParts = r.schedule?.split(" - ")
        val startTime = r.startTime ?: scheduleParts?.getOrNull(0) ?: "—"
        val endTime   = r.endTime   ?: scheduleParts?.getOrNull(1) ?: "—"
        view.findViewById<TextView>(R.id.tvStartDate).text = r.reservationDate ?: "—"
        view.findViewById<TextView>(R.id.tvStartTime).text = startTime
        view.findViewById<TextView>(R.id.tvEndDate).text   = r.endDate ?: r.reservationDate ?: "—"
        view.findViewById<TextView>(R.id.tvEndTime).text   = endTime

        // Estado badge
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        tvStatus.text = when (r.status ?: "") {
            "PENDIENTE" -> "Pendiente"
            "APROBADA"  -> "En préstamo"
            "RECHAZADA" -> "Rechazada"
            "CANCELADA" -> "Cancelada"
            "DEVUELTA"  -> "Devuelta"
            else        -> r.status ?: "—"
        }
        when (r.status ?: "") {
            "PENDIENTE" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_yellow); tvStatus.setTextColor(0xFF92400E.toInt()) }
            "APROBADA"  -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_green);  tvStatus.setTextColor(0xFF065F46.toInt()) }
            "RECHAZADA" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_red);    tvStatus.setTextColor(0xFF991B1B.toInt()) }
            "DEVUELTA"  -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_blue);   tvStatus.setTextColor(0xFF1D4ED8.toInt()) }
            else        -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_gray);   tvStatus.setTextColor(0xFF6B7280.toInt()) }
        }

        // Notas
        view.findViewById<TextView>(R.id.tvPurpose).text = r.purpose?.ifBlank { "—" } ?: "—"
        view.findViewById<TextView>(R.id.tvObservations).text = r.observations?.ifBlank { "—" } ?: "—"

        // Comentario admin
        val sectionAdmin = view.findViewById<LinearLayout>(R.id.sectionAdminComment)
        if (!r.adminComment.isNullOrBlank()) {
            sectionAdmin.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvAdminComment).text = r.adminComment
        } else {
            sectionAdmin.visibility = View.GONE
        }

        // Sección devolución
        val sectionReturn = view.findViewById<LinearLayout>(R.id.sectionReturn)
        if (!r.returnCondition.isNullOrBlank() || !r.returnedAt.isNullOrBlank()) {
            sectionReturn.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvReturnCondition).text = when (r.returnCondition) {
                "BUEN_ESTADO" -> "Buen estado"
                "DAÑADO"      -> "Dañado"
                else          -> r.returnCondition ?: "—"
            }
            view.findViewById<TextView>(R.id.tvReturnedAt).text = formatDateTime(r.returnedAt)
            view.findViewById<TextView>(R.id.tvReturnDescription).text =
                r.returnDescription?.ifBlank { "—" } ?: "—"
        } else {
            sectionReturn.visibility = View.GONE
        }
    }
}
