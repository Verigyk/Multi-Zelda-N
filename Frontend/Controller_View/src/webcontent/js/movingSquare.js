const app = new Vue({
  el: "#app",
  data: {
    top1: 50,
    left1: 50,
    movement : {
      "up" : 0,
      "down" : 0,
      "right" : 0,
      "left" : 0
    },

    connection : null
  },
  computed: {
    squareStyle() {
      return {
        position: "absolute",
        backgroundColor: "red",
        width: "50px",
        height: "50px",
        top: this.top1 + "px",
        left: this.left1 + "px"
      };
    }
  },
  methods: {
    move(distance = 5) {
      direction_sum_vertical = - this.movement["up"] * distance +
                      this.movement["down"] * distance;
      direction_sum_horizontal = - this.movement["left"] * distance +
                      this.movement["right"] * distance;

      this.top1 += direction_sum_vertical;
      this.left1 += direction_sum_horizontal;
    },
  },
  
  mounted() {
    window.addEventListener("keydown", (e) => {
      if (e.key === "z") this.movement["up"] = 1;
      if (e.key === "s") this.movement["down"] = 1;
      if (e.key === "q") this.movement["left"] = 1;
      if (e.key === "d") this.movement["right"] = 1;
    });

    window.addEventListener("keyup", (e) => {
      if (e.key === "z") this.movement["up"] = 0;
      if (e.key === "s") this.movement["down"] = 0;
      if (e.key === "q") this.movement["left"] = 0;
      if (e.key === "d") this.movement["right"] = 0;
    });

    const loop = () => {
      this.move();
      requestAnimationFrame(loop);
    };

    loop();
  }

});