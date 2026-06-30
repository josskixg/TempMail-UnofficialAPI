package providers

import "time"

// waiter is the minimal interface needed by waitForEmail.
type waiter interface {
	GetInbox(email string) ([]Message, error)
}

// waitForEmail polls GetInbox until a message arrives or timeout expires.
func waitForEmail(w waiter, email string, timeout time.Duration, interval time.Duration) (*Message, error) {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		inbox, err := w.GetInbox(email)
		if err != nil {
			return nil, err
		}
		if len(inbox) > 0 {
			return &inbox[0], nil
		}
		time.Sleep(interval)
	}
	return nil, nil
}
