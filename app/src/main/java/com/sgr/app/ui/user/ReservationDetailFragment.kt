package com.sgr.app.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sgr.app.R
import com.sgr.app.model.Reservation
import com.sgr.app.network.RetrofitClient
import com.sgr.app.utils.SessionManager
import kotlinx.coroutines.launch

class ReservationDetailFragment : Fragment() {

    companion object {
        private const val ARG_ID = "reservation_id"

        fun newInstance(reservation: Reservation): ReservationDetailFragment {
            return ReservationDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ID, reservation.id)
                }
            }
        }
    }

    private var reservationId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reservationId = arguments?.getLong(ARG_ID) ?: 0L
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reservation_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        loadDetail(view)
    }

    private fun loadDetail(view: View) {
        val session = SessionManager(requireContext())
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(requireContext())
                    .getMyReservation(reservationId, session.userId)
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

    private fun bind(view: View, r: Reservation) {
        val scheduleParts = r.schedule?.split(" - ")
        val startTime = r.startTime ?: scheduleParts?.getOrNull(0) ?: "—"
        val endTime = r.endTime ?: scheduleParts?.getOrNull(1) ?: "—"

        view.findViewById<TextView>(R.id.tvStartDate).text = r.reservationDate ?: "—"
        view.findViewById<TextView>(R.id.tvStartTime).text = startTime
        view.findViewById<TextView>(R.id.tvEndDate).text = r.endDate ?: r.reservationDate ?: "—"
        view.findViewById<TextView>(R.id.tvEndTime).text = endTime

        // Status badge
        val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
        val statusText = when (r.status ?: "") {
            "PENDIENTE" -> "Pendiente"
            "APROBADA"  -> "En préstamo"
            "RECHAZADA" -> "Rechazada"
            "CANCELADA" -> "Cancelada"
            "DEVUELTA"  -> "Devuelta"
            else        -> r.status ?: "—"
        }
        tvStatus.text = statusText
        when (r.status ?: "") {
            "PENDIENTE" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_yellow); tvStatus.setTextColor(0xFF92400E.toInt()) }
            "APROBADA"  -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_green);  tvStatus.setTextColor(0xFF065F46.toInt()) }
            "RECHAZADA" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_red);    tvStatus.setTextColor(0xFF991B1B.toInt()) }
            "DEVUELTA"  -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_blue);   tvStatus.setTextColor(0xFF1D4ED8.toInt()) }
            else        -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_gray);   tvStatus.setTextColor(0xFF6B7280.toInt()) }
        }

        // Notas
        view.findViewById<TextView>(R.id.tvPurpose).text =
            r.purpose?.ifBlank { "—" } ?: "—"
        view.findViewById<TextView>(R.id.tvObservations).text =
            r.observations?.ifBlank { "—" } ?: "—"
        view.findViewById<TextView>(R.id.tvAdminComment).text =
            r.adminComment?.ifBlank { "—" } ?: "—"

        // Sección devolución
        val sectionReturn = view.findViewById<LinearLayout>(R.id.sectionReturn)
        if (!r.returnCondition.isNullOrBlank() || !r.returnedAt.isNullOrBlank()) {
            sectionReturn.visibility = View.VISIBLE
            val condLabel = when (r.returnCondition) {
                "BUEN_ESTADO" -> "Buen estado"
                "DAÑADO"      -> "Dañado"
                else          -> r.returnCondition ?: "—"
            }
            view.findViewById<TextView>(R.id.tvReturnCondition).text = condLabel
            view.findViewById<TextView>(R.id.tvReturnedAt).text = r.returnedAt ?: "—"
            view.findViewById<TextView>(R.id.tvReturnDescription).text =
                r.returnDescription?.ifBlank { "—" } ?: "—"
        } else {
            sectionReturn.visibility = View.GONE
        }
    }
}
