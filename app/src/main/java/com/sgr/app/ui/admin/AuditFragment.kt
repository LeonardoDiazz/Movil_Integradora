package com.sgr.app.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sgr.app.R
import com.sgr.app.databinding.FragmentAuditBinding
import com.sgr.app.model.Reservation
import com.sgr.app.network.RetrofitClient
import kotlinx.coroutines.launch

class AuditFragment : Fragment() {

    private var _binding: FragmentAuditBinding? = null
    private val binding get() = _binding!!

    private var currentPage = 0
    private var totalPages = 1

    private val tipoLabels = arrayOf("Todos", "Espacio", "Equipo")
    private val tipoValues = arrayOf("", "SPACE", "EQUIPMENT")

    private val estatusLabels = arrayOf("Todos", "Aprobada", "Rechazada", "Cancelada")
    private val estatusValues = arrayOf("", "APROBADA", "RECHAZADA", "CANCELADA")

    private var filterTipo = ""
    private var filterEstatus = ""
    private var isInitializing = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAuditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = ""
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupSpinner(binding.spinnerTipo, tipoLabels) { pos ->
            if (!isInitializing) {
                filterTipo = tipoValues[pos]
                filterEstatus = ""
                currentPage = 0
                load()
            }
        }
        setupSpinner(binding.spinnerEstatus, estatusLabels) { pos ->
            if (!isInitializing) {
                filterEstatus = estatusValues[pos]
                filterTipo = ""
                currentPage = 0
                load()
            }
        }

        isInitializing = false

        binding.btnClearFilters.setOnClickListener {
            filterTipo = ""; filterEstatus = ""
            currentPage = 0
            isInitializing = true
            binding.spinnerTipo.setSelection(0)
            binding.spinnerEstatus.setSelection(0)
            isInitializing = false
            load()
        }

        binding.btnPrev.setOnClickListener { if (currentPage > 0) { currentPage--; load() } }
        binding.btnNext.setOnClickListener { if (currentPage < totalPages - 1) { currentPage++; load() } }

        load()
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
            filterTipo.isNotEmpty() -> filterTipo
            filterEstatus.isNotEmpty() -> filterEstatus
            else -> ""
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.create(requireContext())
                val response = api.getAudit(currentPage, 10, backendFilter)
                if (response.isSuccessful) {
                    val page = response.body() ?: return@launch
                    totalPages = page.totalPages.coerceAtLeast(1)
                    binding.tvPage.text = "Pág ${currentPage + 1} / $totalPages"
                    binding.recyclerView.adapter = AuditAdapter(page.content)
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Error al cargar historial", Toast.LENGTH_SHORT).show()
            } finally { binding.progressBar.visibility = View.GONE }
        }
    }

    private fun showAuditDetailDialog(r: Reservation) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_reservation_detail, null)
        val resourceName = r.resourceName ?: if (r.resourceType == "SPACE") r.spaceName ?: "—" else r.equipmentName ?: "—"
        val resourceTypeLabel = if (r.resourceType == "SPACE") "Espacio" else "Equipo"
        val statusLabel = when (r.status) {
            "PENDIENTE" -> "Pendiente"
            "APROBADA" -> "Aprobada"
            "RECHAZADA" -> "Rechazada"
            "DEVUELTA" -> "Devuelta"
            "CANCELADA" -> "Cancelada"
            else -> r.status ?: "—"
        }

        view.findViewById<TextView>(R.id.tvDId).text = "${r.id}"
        view.findViewById<TextView>(R.id.tvDStatus).text = statusLabel
        view.findViewById<TextView>(R.id.tvDResource).text = resourceName
        view.findViewById<TextView>(R.id.tvDResourceType).text = resourceTypeLabel
        view.findViewById<TextView>(R.id.tvDDate).text = r.reservationDate ?: "—"
        view.findViewById<TextView>(R.id.tvDStartTime).text = r.startTime ?: "—"
        view.findViewById<TextView>(R.id.tvDReturnDate).text = r.endDate ?: r.reservationDate ?: "—"
        view.findViewById<TextView>(R.id.tvDEndTime).text = r.endTime ?: "—"
        view.findViewById<TextView>(R.id.tvDPurpose).text = r.purpose ?: "—"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        view.findViewById<TextView>(R.id.btnDetailClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDetailDismiss)
            .setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class AuditAdapter(
    private val items: List<Reservation>
) : RecyclerView.Adapter<AuditAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_audit, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.view.apply {
            val resourceName = r.resourceName ?: if (r.resourceType == "SPACE") r.spaceName ?: "—" else r.equipmentName ?: "—"
            findViewById<TextView>(R.id.tvResourceName).text = resourceName

            findViewById<TextView>(R.id.tvRequesterName).text = r.requesterName ?: r.requesterEmail ?: "—"

            val tvCategory = findViewById<TextView>(R.id.tvCategory)
            if (r.resourceType == "SPACE") {
                tvCategory.text = "🏢 Espacio"
                tvCategory.setBackgroundResource(R.drawable.bg_chip_category)
                tvCategory.setTextColor(0xFF7C3AED.toInt())
            } else {
                tvCategory.text = "📦 Equipo"
                tvCategory.setBackgroundResource(R.drawable.bg_chip_orange)
                tvCategory.setTextColor(0xFFEA580C.toInt())
            }

            findViewById<TextView>(R.id.tvAuditDate).text = r.reservationDate

            val schedule = r.schedule ?: if (!r.startTime.isNullOrBlank() && !r.endTime.isNullOrBlank())
                "${r.startTime} - ${r.endTime}" else "—"
            findViewById<TextView>(R.id.tvAuditSchedule).text = schedule

            val tvStatus = findViewById<TextView>(R.id.tvAuditStatus)
            val statusLabel = when (r.status) {
                "APROBADA" -> "Aprobada"
                "RECHAZADA" -> "Rechazada"
                "CANCELADA" -> "Cancelada"
                "PENDIENTE" -> "Pendiente"
                "DEVUELTA" -> "Devuelta"
                else -> r.status
            }
            tvStatus.text = statusLabel
            when (r.status ?: "") {
                "PENDIENTE" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_yellow); tvStatus.setTextColor(0xFF92400E.toInt()) }
                "APROBADA"  -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_green);  tvStatus.setTextColor(0xFF065F46.toInt()) }
                "RECHAZADA" -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_red);    tvStatus.setTextColor(0xFF991B1B.toInt()) }
                "DEVUELTA"  -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_blue);   tvStatus.setTextColor(0xFF1D4ED8.toInt()) }
                else        -> { tvStatus.setBackgroundResource(R.drawable.bg_badge_gray);   tvStatus.setTextColor(0xFF6B7280.toInt()) }
            }

        }
    }
}
