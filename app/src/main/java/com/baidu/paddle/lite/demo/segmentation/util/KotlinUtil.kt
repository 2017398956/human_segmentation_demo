package com.baidu.paddle.lite.demo.segmentation.util

import android.app.Activity
import android.app.Dialog
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class KotlinUtil {
    companion object {
        const val TAG = "KotlinUtil"
    }
}

/**
 * 1.通过反射的方法
 */
// 使用方式一、
// private val binding : ActivityTestBinding by inflate()
// 使用方式二、
// private val binding by inflate<ActivityTestBinding>()
inline fun <reified VB : ViewBinding> Activity.inflate() =
    lazy(LazyThreadSafetyMode.NONE) {
        inflateBinding<VB>(layoutInflater).apply { setContentView(root) }
    }

/**
 * 2.通过传 layoutInflater 的方式
 */
// 使用方式一、
// private val binding by viewBinding { layoutInflater ->
//     ActivityTestBinding.inflate(layoutInflater)
// }
// 使用方式二、
// private val binding by viewBinding (ActivityTestBinding::inflate)
inline fun <T : ViewBinding> Activity.viewBinding(crossinline bindingInflater: (LayoutInflater) -> T) =
    lazy(LazyThreadSafetyMode.NONE) {
        val invoke = bindingInflater.invoke(layoutInflater)
        setContentView(invoke.root) //可选
        invoke
    }

inline fun <reified VB : ViewBinding> Dialog.inflate() = lazy {
    inflateBinding<VB>(layoutInflater).apply { setContentView(root) }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified VB : ViewBinding> inflateBinding(layoutInflater: LayoutInflater) =
    VB::class.java.getMethod("inflate", LayoutInflater::class.java)
        .invoke(null, layoutInflater) as VB

/**
 * 因为 FragmentBindingDelegate 中用到了 {Fragment#requireView()} ,所以，需要调用
 * class XXXXFragment : Fragment(R.layout.fragment_main) {...} 且不要在 {Fragment#onViewCreated} 之前使用 ViewBinding.
 */
inline fun <reified VB : ViewBinding> Fragment.bindView() =
    FragmentBindingDelegate(VB::class.java)

class FragmentBindingDelegate<VB : ViewBinding>(private val clazz: Class<VB>) :
    ReadOnlyProperty<Fragment, VB> {

    private var isInitialized = false
    private var _binding: VB? = null
    private val binding: VB get() = _binding!!

    override fun getValue(thisRef: Fragment, property: KProperty<*>): VB {
        if (!isInitialized) {
            thisRef.viewLifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
                /**
                 * On destroy view
                 * 注意：这个方法会在 {@link Fragment#onDestroyView()} 时触发，之后就不能使用 binding 了，
                 * 如果需要回收 自定义 view 的资源可能会有问题。
                 */
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroyView() {
                    Log.d(KotlinUtil.TAG, "KotlinUtil.FragmentBindingDelegate.ON_DESTROY")
                    _binding = null
                }
            })
            _binding = clazz.getMethod("bind", View::class.java)
                .invoke(null, thisRef.requireView()) as VB
            isInitialized = true
        } else {
            if (_binding == null) {
                _binding = clazz.getMethod("bind", View::class.java)
                    .invoke(null, thisRef.requireView()) as VB
            }
        }
        return binding
    }
}

