#include <jni.h>
#include <vector>
#include <fstream>
#include <thread>
#include <algorithm>
#include <limits>
#include <mutex>
#include <stdexcept>
#include <cstdint>
#include <cstring>
#ifdef __ARM_NEON
#include <arm_neon.h>
#endif
namespace {
std::mutex lock;
std::vector<float> weights;
int vocab=0, hidden=0;
void fail(JNIEnv* e,const char* msg) { jclass c=e->FindClass("java/lang/IllegalStateException"); if(c) e->ThrowNew(c,msg); }
float dot(const float* a,const float* b,int n) {
 int i=0; float sum=0;
#ifdef __ARM_NEON
 float32x4_t x=vdupq_n_f32(0), y=x,z=x,w=x;
 for(;i+15<n;i+=16) {
 x=vmlaq_f32(x,vld1q_f32(a+i),vld1q_f32(b+i));
 y=vmlaq_f32(y,vld1q_f32(a+i+4),vld1q_f32(b+i+4));
 z=vmlaq_f32(z,vld1q_f32(a+i+8),vld1q_f32(b+i+8));
 w=vmlaq_f32(w,vld1q_f32(a+i+12),vld1q_f32(b+i+12));
 }
 float tmp[4]; vst1q_f32(tmp,vaddq_f32(vaddq_f32(x,y),vaddq_f32(z,w))); sum=tmp[0]+tmp[1]+tmp[2]+tmp[3];
#endif
 for(;i<n;++i) sum+=a[i]*b[i]; return sum;
}
}
extern "C" JNIEXPORT void JNICALL Java_com_samsung_textgeneration_executor_ModelExecutor_nativeInitLmHead(JNIEnv* e,jobject,jstring path,jint v,jint h) {
 std::lock_guard<std::mutex> guard(lock);
 if(!path || v<=0 || h<=0){fail(e,"Invalid LM head configuration");return;}
 const char* p=e->GetStringUTFChars(path,nullptr); if(!p)return;
 std::ifstream f(p,std::ios::binary|std::ios::ate); e->ReleaseStringUTFChars(path,p);
 try {
 size_t count=static_cast<size_t>(v)*h;
 if(!f || f.tellg()!=static_cast<std::streamoff>(count*sizeof(float))) throw std::runtime_error("Invalid FP32 LM head file size");
 std::vector<float> next(count); f.seekg(0); if(!f.read(reinterpret_cast<char*>(next.data()),count*sizeof(float))) throw std::runtime_error("LM head read failed");
 weights.swap(next); vocab=v; hidden=h;
 } catch(const std::exception& ex){fail(e,ex.what());}
}
extern "C" JNIEXPORT jint JNICALL Java_com_samsung_textgeneration_executor_ModelExecutor_nativeArgmaxLmHead(JNIEnv* e,jobject,jfloatArray a,jint offset) {
 std::lock_guard<std::mutex> guard(lock);
 if(weights.empty() || !a || offset<0 || offset>e->GetArrayLength(a)-hidden){fail(e,"Invalid LM head input");return -1;}
 try {
 std::vector<float> input(hidden); e->GetFloatArrayRegion(a,offset,hidden,input.data()); if(e->ExceptionCheck())return -1;
 const int nt=std::min(vocab,8); const int chunk=(vocab+nt-1)/nt;
 std::vector<int> ids(nt,-1); std::vector<float> vals(nt,-std::numeric_limits<float>::infinity());
 auto worker=[&](int t){for(int v=t*chunk;v<std::min(vocab,(t+1)*chunk);++v){float s=dot(input.data(),weights.data()+static_cast<size_t>(v)*hidden,hidden);if(s>vals[t]){vals[t]=s;ids[t]=v;}}};
 std::vector<std::thread> threads; threads.reserve(nt);
 try {for(int t=0;t<nt;++t)threads.emplace_back(worker,t);} catch(...){for(auto& t:threads)t.join();throw;}
 for(auto& t:threads)t.join(); int best=-1; float val=-std::numeric_limits<float>::infinity();
 for(int t=0;t<nt;++t)if(vals[t]>val){val=vals[t];best=ids[t];}
 if(best<0)fail(e,"No valid LM head score"); return best;
 } catch(const std::exception& ex){fail(e,ex.what());return -1;}
}
extern "C" JNIEXPORT void JNICALL Java_com_samsung_textgeneration_executor_ModelExecutor_nativeReleaseLmHead(JNIEnv*,jobject){std::lock_guard<std::mutex> guard(lock);std::vector<float>().swap(weights);vocab=hidden=0;}
