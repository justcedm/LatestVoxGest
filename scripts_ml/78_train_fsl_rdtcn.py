"""Train the residual dilated FSL TCN candidate."""

from fsl_train_common import parse_args, print_result, train


def main() -> int:
    report = train("rdtcn", parse_args(__doc__))
    print_result(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
