#include "GestureAnalyzer.h"
#include <cmath>
#include <android/log.h>

#define LOG_TAG "GestureCpp"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

GestureAnalyzer::GestureAnalyzer()
        : state_(State::IDLE),
          has_prev_(false),
          cooldown_until_(0),
          swing_time_(0) {}

void GestureAnalyzer::smooth(float& x,float& y)
{
    if(!has_prev_){
        prev_x_=x; prev_y_=y;
        has_prev_=true;
        return;
    }

    x=SMOOTH_ALPHA*x+(1-SMOOTH_ALPHA)*prev_x_;
    y=SMOOTH_ALPHA*y+(1-SMOOTH_ALPHA)*prev_y_;

    prev_x_=x; prev_y_=y;
}

void GestureAnalyzer::pushSample(float x,float y,int64_t t)
{
    traj_.push_back({x,y,t});

    while(!traj_.empty() &&
          t-traj_.front().t>WINDOW_MS)
        traj_.pop_front();

    debug_.point_count=0;
    int i=0;
    for(auto&p:traj_){
        if(i>=12)break;
        debug_.points[i*2]=p.x;
        debug_.points[i*2+1]=p.y;
        i++;
    }
    debug_.point_count=i;
}

bool GestureAnalyzer::detectPrepare()
{
    if(traj_.size()<5) return false;

    auto&f=traj_.front();
    auto&l=traj_.back();

    float dx=l.x-f.x;
    float dy=l.y-f.y;
    float dt=(l.t-f.t)/1000.f;

    float speed=sqrt(dx*dx+dy*dy)/dt;
    bool result = speed < PREPARE_SPEED_MAX;
    LOGD("detectPrepare: speed=%.2f < %.2f → %s", speed, (float)PREPARE_SPEED_MAX, result ? "true" : "false");
    return result;
}

std::optional<GestureAnalyzer::Direction>
GestureAnalyzer::detectSwing()
{
    if(traj_.size()<3) {
        LOGD("detectSwing: traj.size=%zu < 3", traj_.size());
        return std::nullopt;
    }

    auto&f=traj_.front();
    auto&l=traj_.back();

    float dx=l.x-f.x;
    float dy=l.y-f.y;
    float dt=(l.t-f.t)/1000.f;

    float vel=sqrt(dx*dx+dy*dy)/dt;

    debug_.dx=dx;
    debug_.velocity=vel;

    bool disp_ok = fabs(dx) >= DISPLACEMENT_MIN;
    bool vel_ok = vel >= SWING_VEL_MIN;
    LOGD("detectSwing: dx=%.2f disp_min=%.2f %s vel=%.2f vel_min=%.2f %s",
         dx, (float)DISPLACEMENT_MIN, disp_ok ? "✓" : "✗",
         vel, (float)SWING_VEL_MIN, vel_ok ? "✓" : "✗");

    if(!disp_ok) return std::nullopt;
    if(!vel_ok) return std::nullopt;

    Direction dir = dx > 0 ? Direction::RIGHT : Direction::LEFT;
    LOGD("detectSwing: → %s", dir == Direction::RIGHT ? "RIGHT" : "LEFT");
    return dir;
}

bool GestureAnalyzer::detectConfirm(int64_t now)
{
    int64_t time_since_swing = now - swing_time_;
    if(time_since_swing < CONFIRM_DELAY_MS) {
        LOGD("detectConfirm: time_since=%ldms < %dms → false", (long)time_since_swing, CONFIRM_DELAY_MS);
        return false;
    }

    auto&f=traj_.front();
    auto&l=traj_.back();

    float dx=l.x-f.x;
    float dy=l.y-f.y;
    float dt=(l.t-f.t)/1000.f;

    float speed=sqrt(dx*dx+dy*dy)/dt;

    bool result = speed < CONFIRM_SPEED_MAX;
    LOGD("detectConfirm: speed=%.2f < %.2f → %s", speed, (float)CONFIRM_SPEED_MAX, result ? "true" : "false");
    return result;
}

GestureAnalyzer::Result
GestureAnalyzer::update(float x,float y,
                        int64_t ts,bool hand)
{
    Result res;
    debug_.state=(int)state_;

    const char* stateNames[] = {"IDLE", "PREPARE", "CONFIRM_WAIT", "COOLDOWN"};
    LOGD("update: state=%s hand=%d x=%.2f y=%.2f", stateNames[(int)state_], hand ? 1 : 0, x, y);

    if(state_==State::COOLDOWN){
        if(ts>=cooldown_until_) {
            LOGD("state: COOLDOWN→IDLE");
            state_=State::IDLE;
        }
        return res;
    }

    if(!hand){
        LOGD("no hand, reset");
        traj_.clear();
        has_prev_=false;
        state_=State::IDLE;
        return res;
    }

    smooth(x,y);
    LOGD("smoothed: x=%.2f y=%.2f", x, y);
    pushSample(x,y,ts);

    switch(state_)
    {
        case State::IDLE:
            if(detectPrepare()){
                LOGD("state: IDLE→PREPARE");
                state_=State::PREPARE;
            }
            break;

        case State::PREPARE:{
            auto dir=detectSwing();
            if(dir){
                swing_time_=ts;
                res.direction=*dir;
                LOGD("state: PREPARE→CONFIRM_WAIT dir=%s", *dir == Direction::RIGHT ? "RIGHT" : "LEFT");
                state_=State::CONFIRM_WAIT;
            }
            break;
        }

        case State::CONFIRM_WAIT:
            if(detectConfirm(ts)){
                res.triggered=true;
                state_=State::COOLDOWN;
                cooldown_until_=ts+COOLDOWN_MS;
                traj_.clear();
                LOGD("*** TRIGGERED: %s ***", res.direction == Direction::RIGHT ? "RIGHT" : "LEFT");
            }
            break;

        default: break;
    }

    return res;
}