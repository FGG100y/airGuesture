#include <jni.h>
#include "GestureAnalyzer.h"

static GestureAnalyzer analyzer;

extern "C"
JNIEXPORT jint JNICALL
Java_com_device_airctrlguesture_GestureNative_updateGesture(
        JNIEnv*, jobject,
        jfloat x,jfloat y,
        jlong ts,
        jboolean hand)
{
    auto r=analyzer.update(x,y,ts,hand);

    if(!r.triggered) return 0;
    return (int)r.direction;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_device_airctrlguesture_GestureNative_getDebugInfo(
        JNIEnv* env,jobject,
jfloatArray arr)
{
auto& d=analyzer.debugInfo();

jfloat buf[32];
buf[0]=d.dx;
buf[1]=d.velocity;
buf[2]=(float)d.state;
buf[3]=(float)d.point_count;

for(int i=0;i<d.point_count*2;i++)
buf[4+i]=d.points[i];

env->SetFloatArrayRegion(arr,0,32,buf);
}