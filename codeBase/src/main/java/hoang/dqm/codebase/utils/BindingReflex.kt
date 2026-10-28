@file:Suppress("UNCHECKED_CAST")

package hoang.dqm.codebase.utils

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import java.lang.RuntimeException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.ParameterizedType
import java.util.*

object BindingReflex {

    /**
     * ViewBinding
     *
     * @param <V>    ViewBinding
     * @param aClass
     * @param from   layoutInflater
     * @return viewBinding
    </V> */
    fun <V : ViewBinding> reflexViewBinding(aClass: Class<*>, from: LayoutInflater?): V {
        var exception: java.lang.Exception? = null
        try {
            var cursor: Class<*>? = aClass
            while (cursor != null && cursor != Any::class.java) {
                val parent = cursor.genericSuperclass ?: break
                if (parent is ParameterizedType) {
                    for (argument in parent.actualTypeArguments) {
                        val bindingClass = argument as? Class<*> ?: continue
                        if (ViewBinding::class.java.isAssignableFrom(bindingClass)) {
                            val inflate = bindingClass.getMethod("inflate", LayoutInflater::class.java)
                            return inflate.invoke(null, from) as V
                        }
                    }
                    cursor = parent.rawType as? Class<*>
                } else {
                    cursor = parent as? Class<*>
                }
            }
        } catch (e: java.lang.Exception) {
            exception = e
        }
        throw exception ?: RuntimeException("No ViewBinding type declared in ${aClass.name}'s superclass chain")
    }

    /**
     * ViewBinding
     */
    fun <V : ViewBinding> reflexViewBinding(
        aClass: Class<*>,
        from: LayoutInflater?,
        viewGroup: ViewGroup?,
        b: Boolean
    ): V {
        var exception: java.lang.Exception? = null
        try {
            val actualTypeArguments =
                (Objects.requireNonNull(aClass.genericSuperclass) as? ParameterizedType)?.actualTypeArguments
            if (actualTypeArguments != null) {
                for (i in actualTypeArguments.indices) {
                    val tClass: Class<Any>
                    try {
                        tClass = actualTypeArguments[i] as Class<Any>
                    } catch (e: Exception) {
                        continue
                    }
                    if (ViewBinding::class.java.isAssignableFrom(tClass)) {
                        val inflate = tClass.getDeclaredMethod(
                            "inflate",
                            LayoutInflater::class.java,
                            ViewGroup::class.java,
                            Boolean::class.javaPrimitiveType
                        )
                        return inflate.invoke(null, from, viewGroup, b) as V
                    }
                }
                try {
                    return reflexViewBinding<ViewBinding>(
                        aClass.superclass,
                        from,
                        viewGroup,
                        b
                    ) as V
                } catch (e: Exception) {
                    e.printStackTrace()
                    exception = e
                }
            }
        } catch (e: NoSuchMethodException) {
            exception = e
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            exception = e
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            exception = e
            e.printStackTrace()
        } catch (e: java.lang.Exception) {
            exception = e
            e.printStackTrace()
        } finally {
            exception?.printStackTrace()
        }
        throw exception ?: RuntimeException("Error binding")
    }

}
