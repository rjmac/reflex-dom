#include <unistd.h>
#include <pthread.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#include <jni.h>
#include <assert.h>
#include <android/log.h>
#include <HaskellActivity.h>
#include "MainWidget.h"


static int pfd[2];
static pthread_t thr;
static const char *tag = "myapp";

static void *thread_func(void* dummy)
{
    ssize_t rdsz;
    char buf[1000];
    while((rdsz = read(pfd[0], buf, sizeof buf - 1)) > 0) {
        if(buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;  /* add null-terminator */
        __android_log_write(ANDROID_LOG_DEBUG, tag, buf);
    }
    return 0;
}

static int start_logger(const char *app_name)
{
    tag = app_name;

    /* make stdout line-buffered and stderr unbuffered */
    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    /* create the pipe and redirect stdout and stderr */
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    /* spawn the logging thread */
    if(pthread_create(&thr, 0, thread_func, 0) == -1)
        return -1;
    pthread_detach(thr);
    return 0;
}

jobject Reflex_Dom_Android_MainWidget_start(jobject activity, const char *url, const JSaddleCallbacks *jsaddleCallbacks) {
  assert(activity);
  assert(url);

  if(open("/tmp/rjm-suppress", O_RDONLY) == -1) {
    start_logger("MCSTDOUT");
  }

  JNIEnv *env;
  jint attachResult = (*HaskellActivity_jvm)->AttachCurrentThread(HaskellActivity_jvm, &env, NULL);
  assert(attachResult == JNI_OK);

  jclass cls = (*env)->FindClass(env, "org/reflexfrp/reflexdom/MainWidget");
  assert(cls);
  jmethodID startMainWidget = (*env)->GetStaticMethodID(env, cls, "startMainWidget", "(Landroid/app/Activity;Ljava/lang/String;JLjava/lang/String;)Ljava/lang/Object;");
  assert(startMainWidget);

  jstring jurl = (*env)->NewStringUTF(env, url);
  assert(jurl);
  jstring initialJS = (*env)->NewStringUTF(env, jsaddleCallbacks->jsaddleJsData);
  jobject result = (*env)->CallStaticObjectMethod(env, cls, startMainWidget, activity, jurl, (jlong)jsaddleCallbacks, initialJS);
  (*env)->DeleteLocalRef(env, initialJS);
  if((*env)->ExceptionOccurred(env)) {
    __android_log_write(ANDROID_LOG_DEBUG, "MainWidget", "startMainWidget exception");
    (*env)->ExceptionDescribe(env);
  }
  return (*env)->NewGlobalRef(env, result);
}

void Reflex_Dom_Android_MainWidget_runJS(jobject jsExecutor, const char* js) {
  size_t i;
  JNIEnv *env;
  jint attachResult = (*HaskellActivity_jvm)->AttachCurrentThread(HaskellActivity_jvm, &env, NULL);
  assert (attachResult == JNI_OK);

  (*env)->PushLocalFrame(env, 5);

  //TODO: Don't search for this method every time
  jclass cls = (*env)->GetObjectClass(env, jsExecutor);
  assert(cls);
  jmethodID evaluateJavascript = (*env)->GetMethodID(env, cls, "evaluateJavascript", "(Ljava/lang/String;)V");
  assert(evaluateJavascript);
  __android_log_print(ANDROID_LOG_DEBUG, "MCTAG", "JS to run: %p", js);
  for(i = 0; i < strlen(js); i += 1024) {
    __android_log_print(ANDROID_LOG_DEBUG, "MCTAG", "%s", js + i);
  }
  jstring js_str = (*env)->NewStringUTF(env, js);
  (*env)->CallVoidMethod(env, jsExecutor, evaluateJavascript, js_str, 0);
  if((*env)->ExceptionOccurred(env)) {
    __android_log_write(ANDROID_LOG_DEBUG, "MainWidget", "runJS exception");
    (*env)->ExceptionDescribe(env);
  }

  (*env)->PopLocalFrame(env, 0);
}

JNIEXPORT void JNICALL Java_org_reflexfrp_reflexdom_MainWidget_00024JSaddleCallbacks_startProcessing (JNIEnv *env, jobject thisObj, jlong callbacksLong) {
  const JSaddleCallbacks *callbacks = (const JSaddleCallbacks *)callbacksLong;
  (*(callbacks->jsaddleStart))();
  return;
}

JNIEXPORT void JNICALL Java_org_reflexfrp_reflexdom_MainWidget_00024JSaddleCallbacks_processMessage (JNIEnv *env, jobject thisObj, jlong callbacksLong, jstring msg) {
  const JSaddleCallbacks *callbacks = (const JSaddleCallbacks *)callbacksLong;
  const char *msg_str = (*env)->GetStringUTFChars(env, msg, NULL);
  printf("procmes: %s\n", msg_str);
  (*(callbacks->jsaddleResult))(msg_str);
  (*env)->ReleaseStringUTFChars(env, msg, msg_str);
  return;
}

JNIEXPORT jstring JNICALL Java_org_reflexfrp_reflexdom_MainWidget_00024JSaddleCallbacks_processSyncMessage (JNIEnv *env, jobject thisObj, jlong callbacksLong, jstring msg) {
  const JSaddleCallbacks *callbacks = (const JSaddleCallbacks *)callbacksLong;
  const char *msg_str = (*env)->GetStringUTFChars(env, msg, NULL);
  printf("procsyncmes: %s\n", msg_str);
  char *next_str = (*(callbacks->jsaddleSyncResult))(msg_str);
  printf("procsyncres: %s\n", next_str);
  jstring next_jstr = (*env)->NewStringUTF(env,next_str);
  free(next_str);
  (*env)->ReleaseStringUTFChars(env, msg, msg_str);
  return next_jstr;
}
