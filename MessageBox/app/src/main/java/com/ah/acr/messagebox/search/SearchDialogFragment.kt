package com.ah.acr.messagebox.search

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ah.acr.messagebox.R
import com.ah.acr.messagebox.database.AddressEntity
import com.ah.acr.messagebox.database.AddressViewModel
import com.ah.acr.messagebox.databinding.DialogSearchBinding

// 1. 검색 다이얼로그 클래스
class SearchDialogFragment : DialogFragment() {

    private lateinit var binding: DialogSearchBinding
    private lateinit var searchAdapter: SearchAdapter

    private lateinit var addressViewModel: AddressViewModel
    private val searchResults = mutableListOf<AddressEntity>()

//    // 검색 데이터 (실제로는 데이터베이스나 API에서 가져옴)
//    private val allItems = listOf(
//        SearchItem(1, "안드로이드 개발", "모바일 앱 개발"),
//        SearchItem(2, "코틀린 프로그래밍", "현대적인 프로그래밍 언어"),
//        SearchItem(3, "자바 기초", "객체지향 프로그래밍"),
//        SearchItem(4, "리액트 네이티브", "크로스 플랫폼 개발"),
//        SearchItem(5, "플러터 개발", "구글의 UI 툴킷"),
//        SearchItem(6, "스프링 부트", "자바 웹 프레임워크"),
//        SearchItem(7, "노드js", "자바스크립트 런타임"),
//        SearchItem(8, "파이썬 머신러닝", "인공지능 개발")
//    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addressViewModel = ViewModelProvider(this).get(AddressViewModel::class.java)

        setupRecyclerView()
        setupSearchView()
        setupClickListeners()

        // 초기에는 모든 항목 표시
        addressViewModel.allAddress.observe(viewLifecycleOwner) { addressList ->
            updateSearchResults(addressList)
        }

    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(searchResults) { selectedItem ->
            // 아이템 선택 시 처리
            onItemSelected(selectedItem)
        }

        binding.recyclerViewResults.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupSearchView() {
        binding.searchView.apply {
            // SearchView 설정
            isSubmitButtonEnabled = false
            queryHint = "검색어를 입력하세요"

            // 검색 리스너 설정
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    // 엔터 키 누를 때 처리 (선택사항)
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // 텍스트 변경될 때마다 실시간 검색
                    performSearch(newText ?: "")
                    return true
                }
            })

            // SearchView가 열릴 때 자동으로 포커스
            isIconified = false
            requestFocusFromTouch()
        }
    }

    private fun setupClickListeners() {
        // 취소 버튼
//        binding.btnCancel.setOnClickListener {
//            dismiss()
//        }

        // 다이얼로그 외부 클릭 시 닫기
        binding.root.setOnClickListener {
            dismiss()
        }

        // 검색 영역 클릭 시 이벤트 차단
        binding.cardSearch.setOnClickListener {
            // 클릭 이벤트 차단 (다이얼로그가 닫히지 않도록)
        }
    }

    private fun performSearch(query: String) {

        addressViewModel.getSearchAddress(query).observe(viewLifecycleOwner) { addrList ->

            Log.v("search", addrList.isEmpty().toString());
            updateSearchResults(addrList)
            updateEmptyState(addrList.isEmpty() && query.isNotEmpty())
        }
    }

    private fun updateSearchResults(items: List<AddressEntity>) {
        searchResults.clear()
        searchResults.addAll(items)

        searchAdapter.notifyDataSetChanged()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.textViewEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewResults.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun onItemSelected(item: AddressEntity) {
        // 선택된 아이템 처리
        val result = Bundle().apply {
            putInt("selected_id", item.id)
            putString("selected_nic", item.numbersNic)
            putString("selected_code", item.numbers)
        }

        // Fragment Result API 사용
        parentFragmentManager.setFragmentResult("search_result", result)
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        // 다이얼로그 크기 설정
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// 3. 검색 결과 어댑터
class SearchAdapter(
    private val items: List<AddressEntity>,
    private val onItemClick: (AddressEntity) -> Unit
) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textTitle: TextView = view.findViewById(R.id.textTitle)
        val textDescription: TextView = view.findViewById(R.id.textDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.textTitle.text = item.numbersNic
        holder.textDescription.text = item.numbers

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size
}