#pragma once
#include <deque>
#include <cstdint>
#include <optional>

class GestureAnalyzer {
public:

    enum class Direction {
        NONE = 0,
        LEFT = -1,
        RIGHT = 1
    };

    struct Result {
        bool triggered = false;
        Direction direction = Direction::NONE;
    };

    struct DebugInfo {
        float dx = 0;
        float velocity = 0;
        int state = 0;
        float points[24];
        int point_count = 0;
    };

public:
    GestureAnalyzer();

    Result update(float x, float y,
                  int64_t timestamp,
                  bool hand_present);

    const DebugInfo& debugInfo() const { return debug_; }

private:

    struct Sample {
        float x,y;
        int64_t t;
    };

    enum class State {
        IDLE,
        PREPARE,
        CONFIRM_WAIT,
        COOLDOWN
    };

private:
    void smooth(float& x,float& y);
    void pushSample(float x,float y,int64_t t);

    bool detectPrepare();
    std::optional<Direction> detectSwing();
    bool detectConfirm(int64_t now);
    float checkDirectionConsistency() const;
    int64_t getGestureDuration() const;

private:
    std::deque<Sample> traj_;

    State state_;

    bool has_prev_;
    float prev_x_,prev_y_;

    int64_t cooldown_until_;
    int64_t swing_time_;
    Direction detected_direction_;

    DebugInfo debug_;

private:
    static constexpr float SMOOTH_ALPHA=0.7f;
    static constexpr int WINDOW_MS=400;

    static constexpr float PREPARE_SPEED_MAX=0.5f;
    static constexpr float SWING_VEL_MIN=0.35f;
    static constexpr float DISPLACEMENT_MIN=0.06f;
    static constexpr float CONFIRM_SPEED_MAX=0.7f;

    static constexpr int MIN_SWIPE_DURATION_MS=100;
    static constexpr int MAX_SWIPE_DURATION_MS=500;
    static constexpr float DIRECTION_CONSISTENCY_MIN=0.65f;

    static constexpr int CONFIRM_DELAY_MS=50;
    static constexpr int COOLDOWN_MS=800;
};
