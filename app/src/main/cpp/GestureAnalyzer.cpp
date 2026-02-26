#include "GestureAnalyzer.h"
#include <cmath>

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
    return speed<PREPARE_SPEED_MAX;
}

std::optional<GestureAnalyzer::Direction>
GestureAnalyzer::detectSwing()
{
    if(traj_.size()<3) return std::nullopt;

    auto&f=traj_.front();
    auto&l=traj_.back();

    float dx=l.x-f.x;
    float dy=l.y-f.y;
    float dt=(l.t-f.t)/1000.f;

    float vel=sqrt(dx*dx+dy*dy)/dt;

    debug_.dx=dx;
    debug_.velocity=vel;

    if(fabs(dx)<DISPLACEMENT_MIN) return std::nullopt;
    if(vel<SWING_VEL_MIN) return std::nullopt;

    return dx>0?Direction::RIGHT:Direction::LEFT;
}

bool GestureAnalyzer::detectConfirm(int64_t now)
{
    if(now-swing_time_<CONFIRM_DELAY_MS)
        return false;

    auto&f=traj_.front();
    auto&l=traj_.back();

    float dx=l.x-f.x;
    float dy=l.y-f.y;
    float dt=(l.t-f.t)/1000.f;

    float speed=sqrt(dx*dx+dy*dy)/dt;

    return speed<CONFIRM_SPEED_MAX;
}

GestureAnalyzer::Result
GestureAnalyzer::update(float x,float y,
                        int64_t ts,bool hand)
{
    Result res;
    debug_.state=(int)state_;

    if(state_==State::COOLDOWN){
        if(ts>=cooldown_until_)
            state_=State::IDLE;
        return res;
    }

    if(!hand){
        traj_.clear();
        has_prev_=false;
        state_=State::IDLE;
        return res;
    }

    smooth(x,y);
    pushSample(x,y,ts);

    switch(state_)
    {
        case State::IDLE:
            if(detectPrepare())
                state_=State::PREPARE;
            break;

        case State::PREPARE:{
            auto dir=detectSwing();
            if(dir){
                swing_time_=ts;
                res.direction=*dir;
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
            }
            break;

        default: break;
    }

    return res;
}