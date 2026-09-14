package dev.jason.gboardpatches.extension.geminitranslation;

import android.content.Context;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Registers the synthetic "Translate" Access Point on Gboard 18.0.3.
 *
 * <p>Mirrors the Floating Web Search contribution: the token is appended to the order catalog so
 * the item can be pinned/reordered, and a descriptor is registered on the controller with a
 * runnable that hands control to {@link GboardGeminiTranslationRuntime}.
 */
public final class GboardGeminiTranslationAccessPoint1803Contribution {
    public static final GboardGeminiTranslationAccessPoint1803Contribution INSTANCE =
            new GboardGeminiTranslationAccessPoint1803Contribution();
    public static final String TOKEN = "gemini_translate";
    /** Candidate stock translate drawables, resolved by name so no ID is guessed. */
    static final String[] TRANSLATE_DRAWABLE_NAMES = {
            "quantum_gm_ic_translate_vd_theme_24",
            "quantum_gm_ic_g_translate_vd_theme_24",
            "ic_translate",
    };
    /** Google "G" drawable (verified on 18.0.3) used when no translate icon can be resolved. */
    static final int GOOGLE_G_DRAWABLE_ID = 0x7f08048b;

    private static volatile Handles handles;

    private GboardGeminiTranslationAccessPoint1803Contribution() {
    }

    public Object extendOrderCatalog(Context context, Object original) {
        try {
            if (!GboardGeminiTranslationRuntime.isEnabled(context)
                    || !(original instanceof Collection<?> collection)) {
                return original;
            }
            List<String> values = copyStrings(collection);
            if (!values.contains(TOKEN)) {
                values.add(TOKEN);
            }
            Class<?> immutableCollection = Class.forName(
                    "vxe", false, original.getClass().getClassLoader());
            Method copy = immutableCollection.getDeclaredMethod("n", Collection.class);
            copy.setAccessible(true);
            return copy.invoke(null, values);
        } catch (Throwable ignored) {
            return original;
        }
    }

    public void register(Object controller, Context context) {
        try {
            if (controller == null || context == null
                    || !GboardGeminiTranslationRuntime.isEnabled(context)) {
                return;
            }
            GboardGeminiTranslationRuntime.rememberInputMethodService(context);
            Context application = context.getApplicationContext();
            Context safeContext = application != null ? application : context;
            Handles active = handles(controller.getClass().getClassLoader());
            Object builder = active.descriptorBuilderFactory.invoke(null);
            active.builderTokenMethod.invoke(builder, TOKEN);
            active.builderIconResourceMethod.invoke(builder, resolveIcon(safeContext));
            active.builderLabelTextField.set(builder, "Gemini Translate");
            active.builderContentDescriptionTextField.set(builder, "Gemini Translate");
            active.builderRunnableMethod.invoke(builder, new TranslateAction(safeContext));
            Object descriptor = active.builderBuildMethod.invoke(builder);
            active.controllerRegisterMethod.invoke(controller, descriptor, false);
        } catch (Throwable ignored) {
            // A synthetic Access Point must fail closed.
        }
    }

    static int resolveIcon(Context context) {
        try {
            String packageName = context.getPackageName();
            for (String name : TRANSLATE_DRAWABLE_NAMES) {
                int id = context.getResources().getIdentifier(name, "drawable", packageName);
                if (id != 0) {
                    return id;
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the Google G drawable below.
        }
        return GOOGLE_G_DRAWABLE_ID;
    }

    static List<String> copyStrings(Collection<?> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                if (value instanceof String stringValue && !result.contains(stringValue)) {
                    result.add(stringValue);
                }
            }
        }
        return result;
    }

    private static Handles handles(ClassLoader classLoader) throws Throwable {
        Handles current = handles;
        if (current != null && current.classLoader == classLoader) {
            return current;
        }
        synchronized (GboardGeminiTranslationAccessPoint1803Contribution.class) {
            current = handles;
            if (current == null || current.classLoader != classLoader) {
                current = Handles.resolve(classLoader);
                handles = current;
            }
            return current;
        }
    }

    private static final class TranslateAction implements Runnable {
        private final WeakReference<Context> contextReference;

        TranslateAction(Context context) {
            contextReference = new WeakReference<>(context);
        }

        @Override
        public void run() {
            try {
                GboardGeminiTranslationRuntime.openTranslationBox(contextReference.get());
            } catch (Throwable ignored) {
                // The toolbar click path must never throw into Gboard.
            }
        }
    }

    private static final class Handles {
        final ClassLoader classLoader;
        final Method descriptorBuilderFactory;
        final Method builderTokenMethod;
        final Method builderRunnableMethod;
        final Method builderBuildMethod;
        final Method builderIconResourceMethod;
        final Field builderLabelTextField;
        final Field builderContentDescriptionTextField;
        final Method controllerRegisterMethod;

        Handles(ClassLoader classLoader, Method descriptorBuilderFactory,
                Method builderTokenMethod, Method builderRunnableMethod,
                Method builderBuildMethod, Method builderIconResourceMethod,
                Field builderLabelTextField, Field builderContentDescriptionTextField,
                Method controllerRegisterMethod) {
            this.classLoader = classLoader;
            this.descriptorBuilderFactory = descriptorBuilderFactory;
            this.builderTokenMethod = builderTokenMethod;
            this.builderRunnableMethod = builderRunnableMethod;
            this.builderBuildMethod = builderBuildMethod;
            this.builderIconResourceMethod = builderIconResourceMethod;
            this.builderLabelTextField = builderLabelTextField;
            this.builderContentDescriptionTextField = builderContentDescriptionTextField;
            this.controllerRegisterMethod = controllerRegisterMethod;
        }

        static Handles resolve(ClassLoader classLoader) throws Throwable {
            Class<?> descriptor = Class.forName("mic", false, classLoader);
            Class<?> builder = Class.forName("mhx", false, classLoader);
            Class<?> controller = Class.forName("mlh", false, classLoader);
            Method descriptorBuilderFactory = descriptor.getDeclaredMethod("c");
            Method builderTokenMethod = builder.getDeclaredMethod("l", String.class);
            Method builderRunnableMethod = builder.getDeclaredMethod("q", Runnable.class);
            Method builderBuildMethod = builder.getDeclaredMethod("a");
            Method builderIconResourceMethod = builder.getDeclaredMethod("i", int.class);
            Field builderLabelTextField = builder.getDeclaredField("d");
            Field builderContentDescriptionTextField = builder.getDeclaredField("e");
            Method controllerRegisterMethod = controller.getDeclaredMethod(
                    "g", descriptor, boolean.class);
            descriptorBuilderFactory.setAccessible(true);
            builderTokenMethod.setAccessible(true);
            builderRunnableMethod.setAccessible(true);
            builderBuildMethod.setAccessible(true);
            builderIconResourceMethod.setAccessible(true);
            builderLabelTextField.setAccessible(true);
            builderContentDescriptionTextField.setAccessible(true);
            controllerRegisterMethod.setAccessible(true);
            return new Handles(classLoader, descriptorBuilderFactory, builderTokenMethod,
                    builderRunnableMethod, builderBuildMethod, builderIconResourceMethod,
                    builderLabelTextField, builderContentDescriptionTextField,
                    controllerRegisterMethod);
        }
    }
}
