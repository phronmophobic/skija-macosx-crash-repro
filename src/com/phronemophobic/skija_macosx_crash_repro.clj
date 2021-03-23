(ns com.phronemophobic.skija-macosx-crash-repro
  (:import com.sun.jna.Pointer
           com.sun.jna.Memory
           com.sun.jna.ptr.FloatByReference
           com.sun.jna.ptr.IntByReference
           com.sun.jna.IntegerType
           java.awt.image.BufferedImage)
  (:import
   java.nio.ByteBuffer
   [org.jetbrains.skija BackendRenderTarget Canvas ColorSpace DirectContext FramebufferFormat Paint Rect RRect Surface SurfaceColorFormat SurfaceOrigin FontMgr FontStyle Font Path PaintMode Data Image]
   [org.lwjgl.glfw Callbacks GLFW GLFWErrorCallback
    GLFWMouseButtonCallback
    GLFWKeyCallback
    GLFWCursorPosCallback
    GLFWScrollCallback
    GLFWFramebufferSizeCallback
    GLFWWindowRefreshCallback
    GLFWDropCallback
    GLFWCharCallback]
   [org.lwjgl.opengl GL GL11]
   [org.lwjgl.system MemoryUtil])
  (:gen-class))

(def void Void/TYPE)
(def main-class-loader @clojure.lang.Compiler/LOADER)



(deftype DispatchCallback [f]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class  [Pointer]))
  (getReturnType [_]
    void)
  (callback ^void [_ args]
    (.setContextClassLoader (Thread/currentThread) main-class-loader)

    (import 'com.sun.jna.Native)
    ;; https://java-native-access.github.io/jna/4.2.1/com/sun/jna/Native.html#detach-boolean-
    ;; for some other info search https://java-native-access.github.io/jna/4.2.1/ for CallbackThreadInitializer

    ;; turning off detach here might give a performance benefit,
    ;; but more importantly, it prevents jna from spamming stdout
    ;; with "JNA: could not detach thread"
    (com.sun.jna.Native/detach false)
    (f)
    ;; need turn detach back on so that
    ;; we don't prevent the jvm exiting
    ;; now that we're done
    (com.sun.jna.Native/detach true)))



(defn run* [do-not-crash?]
  (.set (GLFWErrorCallback/createPrint System/err))
  (GLFW/glfwInit)
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
  (let [window (GLFW/glfwCreateWindow 400 400 "Title" MemoryUtil/NULL MemoryUtil/NULL)]

    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwSwapInterval 1)
    (GLFW/glfwShowWindow window)  
    (GL/createCapabilities)
    (let [context (DirectContext/makeGL)
          fb-id   (GL11/glGetInteger 0x8CA6)]

      

      (GLFW/glfwPollEvents)
      (Callbacks/glfwFreeCallbacks window)
      (GLFW/glfwHideWindow window)
      (GLFW/glfwDestroyWindow window)
      (when do-not-crash?
        (.close context))      
      (GLFW/glfwTerminate)
      (.free (GLFW/glfwSetErrorCallback nil))
      (System/gc))
    ))

(def objlib (try
              (com.sun.jna.NativeLibrary/getInstance "CoreFoundation")
              (catch UnsatisfiedLinkError e
                    nil)))

(def main-queue (when objlib
                  (.getGlobalVariableAddress ^com.sun.jna.NativeLibrary objlib "_dispatch_main_q")))

(def dispatch_sync (when objlib
                     (.getFunction ^com.sun.jna.NativeLibrary objlib "dispatch_sync_f")))

(defonce callbacks (atom []))

(defn- dispatch-sync [f]
  (if (and main-queue dispatch_sync)
    (let [callback (DispatchCallback. f)
          args (to-array [main-queue nil callback])]
      (.invoke ^com.sun.jna.Function dispatch_sync void args)
      ;; please don't garbage collect me :D
      (identity args)
      nil)
    (f)))


(defn run [do-not-crash?]
  (dispatch-sync #(run* do-not-crash?)))


(defn run-test []
  (while true
    (run true)
    (Thread/sleep 500)))






