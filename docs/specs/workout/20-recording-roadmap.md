# Recording Roadmap

1. ui레이아웃을 정리한다
2. 타이머의 기능을 세분화한다.
먼저 rest 시작이랑 push(뭐가되든 운동시작이라는 거) 를 놓는다.
rest일때는 시간이 흐르고 운동이 시작하면 시간이 멈춘다
운동이 시작하고 rest를 누르기전에 했던 운동종목과 횟수 무게를 적어야한다.(종목과 무게는 유지가되고 횟수는 매번 다시 입력해야함)
운동종목은 본인이 자유롭게 적을수있다.
예를들어 벤치프레스는 bcpr 이런식으로 본인 마음대로적는다
3. 수행한 종목과 횟수 휴식시간을 기록할수있어야한다.
예를들어서 저번에는 2번째 세트에 10번했는데 이번엔 9번밖에 못했다 다는 식의 테이블이나 그래프가 보이면 눌러보고 이전에 이 운동을 했을때랑 휴식시간이 얼마나 차이가 있었는지를 볼수있어야한다
4. 어떤 운동 종목을 선택하면 이걸 언제 했는지 평균 몇개했는지를 볼수있다.
이때 설정에 따라서 하루를 한칸으로 두고 할지 횟수를 한칸으로 두고 할지를 볼수있다.
예를들어 하루를 한칸으로 두고 일주일에 한번했으면 남은 6일동안은 점은 없지만 두 점을 잇는선은 있어야한다.
1회를 한칸으로 두면 매 공간마다 점이 있어서 연결되어있어야함(이게 디폴트)
5. 테스트모드가 있어야함 내가 안에 임의의 데이터를 넣을수있어야함
6. 앱을 처음키면 운동시작이랑 기록보기 설정 등이 있어야함
## Implementation Status
These are future requirements and do not describe implemented functionality.

## Example
- Input: A future record with exercise, weight, repetitions and rest duration.
- Steps: Save a set and open history once recording is implemented.
- Expected result: Previous sets can be compared.

- [Current Editing Specification](10-edit-mode.md)
