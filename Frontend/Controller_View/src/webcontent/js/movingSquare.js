const squareBox = new Vue({
  el: "#squareBox",
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


  methods: {


    move(distance = 5) {
      direction_sum_vertical = - this.movement["up"] * distance +
                      this.movement["down"] * distance;
      direction_sum_horizontal = - this.movement["left"] * distance +
                      this.movement["right"] * distance;

      this.top1 += direction_sum_vertical;
      this.left1 += direction_sum_horizontal;

      const squareBoxElt = document.getElementById("squareBox");
      squareBoxElt.style.top = this.top1 + "px",
      squareBoxElt.style.left = this.left1 + "px"

    },


    keyMove(key, isPressed) {
      if (key === "z") this.movement["up"] = isPressed;
      if (key === "s") this.movement["down"] = isPressed;
      if (key === "q") this.movement["left"] = isPressed;
      if (key === "d") this.movement["right"] = isPressed;
    }
  },
  

});

const game = new Vue({

  el: "#game",

  mounted() {

    window.addEventListener("keydown", (e) => {
      squareBox.keyMove(e.key, 1);
    });

    window.addEventListener("keyup", (e) => {
      squareBox.keyMove(e.key, 0);
    });

    const loop = () => {
      squareBox.move();
      requestAnimationFrame(loop);
    };

    loop();

  }
});

